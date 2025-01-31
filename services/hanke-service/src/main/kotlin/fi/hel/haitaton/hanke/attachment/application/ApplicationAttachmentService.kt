package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING_CLIENT
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ApplicationAlreadyProcessingException
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentContentService
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
import fi.hel.haitaton.hanke.attachment.common.hasInfected
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime.now
import java.util.UUID
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentService(
    private val cableReportService: CableReportService,
    private val applicationRepository: ApplicationRepository,
    private val attachmentRepository: ApplicationAttachmentRepository,
    private val attachmentContentService: AttachmentContentService,
    private val scanClient: FileScanClient,
) {
    @Transactional(readOnly = true)
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadata> =
        attachmentRepository.findByApplicationId(applicationId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getContent(applicationId: Long, attachmentId: UUID): AttachmentContent {
        val attachment = findAttachment(applicationId, attachmentId)
        val content = attachmentContentService.findApplicationContent(attachmentId)
        with(attachment) {
            return AttachmentContent(fileName, contentType, content)
        }
    }

    /**
     * Attachment can be added if application has not proceeded to HANDLING or later status. It will
     * be sent immediately if application is in Allu (alluId present).
     */
    @Transactional
    fun addAttachment(
        applicationId: Long,
        attachmentType: ApplicationAttachmentType,
        attachment: MultipartFile
    ): ApplicationAttachmentMetadata {
        val application =
            findApplication(applicationId).also { application ->
                ensureApplicationIsPending(application)
                ensureRoomForAttachment(applicationId)
                ensureValidFile(attachment)
            }

        val entity =
            ApplicationAttachmentEntity(
                id = null,
                fileName = attachment.originalFilename!!,
                contentType = attachment.contentType!!,
                createdByUserId = currentUserId(),
                createdAt = now(),
                attachmentType = attachmentType,
                applicationId = application.id!!,
            )

        val newAttachment = attachmentRepository.save(entity)
        attachmentContentService.saveApplicationContent(newAttachment.id!!, attachment.bytes)

        application.alluid?.let {
            cableReportService.addAttachment(it, newAttachment.toAlluAttachment(attachment.bytes))
        }

        return newAttachment.toDto().also {
            logger.info {
                "Added attachment ${it.id} to application $applicationId with size ${attachment.bytes.size}"
            }
        }
    }

    /** Attachment can be deleted if the application has not been sent to Allu (alluId null). */
    @Transactional
    fun deleteAttachment(applicationId: Long, attachmentId: UUID) {
        val attachment = findAttachment(applicationId, attachmentId)
        val application = findApplication(applicationId)

        if (isInAllu(application)) {
            logger.warn { "Application $applicationId is in Allu, attachments cannot be deleted." }
            throw ApplicationInAlluException(application.id, application.alluid)
        }

        attachmentRepository.deleteById(attachment.id!!)
        logger.info { "Deleted application attachment ${attachment.id}" }
    }

    @Transactional(readOnly = true)
    fun sendInitialAttachments(alluId: Int, applicationId: Long) {
        logger.info { "Sending initial attachments for application, alluid=$alluId" }
        val attachments = attachmentRepository.findByApplicationId(applicationId)
        if (attachments.isEmpty()) {
            logger.info { "No attachments to send for alluId $alluId" }
            return
        }

        cableReportService.addAttachments(alluId, attachments) {
            attachmentContentService.findApplicationContent(it)
        }
    }

    private fun findAttachment(
        applicationId: Long,
        attachmentId: UUID
    ): ApplicationAttachmentEntity =
        attachmentRepository.findByApplicationIdAndId(applicationId, attachmentId)
            ?: throw AttachmentNotFoundException(attachmentId)

    private fun findApplication(applicationId: Long): ApplicationEntity =
        applicationRepository.findById(applicationId).orElseThrow {
            ApplicationNotFoundException(applicationId)
        }

    private fun ensureValidFile(attachment: MultipartFile) =
        with(attachment) {
            AttachmentValidator.validate(this)
            val scanResult = scanClient.scan(listOf(FileScanInput(originalFilename!!, bytes)))
            if (scanResult.hasInfected()) {
                throw AttachmentInvalidException("Infected file detected, see previous logs.")
            }
        }

    private fun ensureApplicationIsPending(application: ApplicationEntity) {
        if (!isPending(application.alluid, application.alluStatus)) {
            logger.warn { "Application is processing, cannot add attachment." }
            throw ApplicationAlreadyProcessingException(application.id, application.alluid)
        }
    }

    private fun ensureRoomForAttachment(applicationId: Long) {
        if (attachmentAmountReached(applicationId)) {
            logger.warn {
                "Application $applicationId has reached the allowed amount of attachments."
            }
            throw AttachmentInvalidException("Attachment amount limit reached")
        }
    }

    /** Application considered pending if no alluId or status null, pending, or pending_client. */
    private fun isPending(alluId: Int?, alluStatus: ApplicationStatus?): Boolean {
        alluId ?: return true
        return when (alluStatus) {
            null,
            PENDING,
            PENDING_CLIENT -> alluPending(alluId)
            else -> false
        }
    }

    /** Check current status from Allu. */
    private fun alluPending(alluId: Int): Boolean {
        val status = cableReportService.getApplicationInformation(alluId).status
        return listOf(PENDING, PENDING_CLIENT).contains(status)
    }

    private fun isInAllu(application: ApplicationEntity): Boolean = application.alluid != null

    private fun attachmentAmountReached(applicationId: Long): Boolean {
        val attachmentCount = attachmentRepository.countByApplicationId(applicationId)
        logger.info {
            "Application $applicationId contains $attachmentCount attachments beforehand."
        }
        return attachmentCount >= ALLOWED_ATTACHMENT_COUNT
    }
}

class ApplicationInAlluException(id: Long?, alluId: Int?) :
    RuntimeException("Application is already sent to Allu, applicationId=$id, alluId=$alluId")
