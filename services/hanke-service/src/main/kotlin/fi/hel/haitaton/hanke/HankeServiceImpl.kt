package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.ContactType.MUU
import fi.hel.haitaton.hanke.ContactType.OMISTAJA
import fi.hel.haitaton.hanke.ContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.ContactType.TOTEUTTAJA
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.application.toNewApplication
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeWithApplications
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.domain.toEntity
import fi.hel.haitaton.hanke.geometria.GeometriatService
import fi.hel.haitaton.hanke.logging.AuditLogService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.YhteystietoLoggingEntryHolder
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.Role
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import fi.hel.haitaton.hanke.validation.HankePublicValidator
import java.time.ZonedDateTime
import mu.KotlinLogging
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/*
 * Helper for mapping and sorting data to existing collections.
 *
 * Example usage:
 * mergeDataInto(hanke.alueet, entity.listOfHankeAlueet) { source, target ->
 *    copyNonNullHankealueFieldsToEntity(hanke, source, target)
 * }
 * - Transforms data from hanke.alueet into entity.listOfHankeAlueet
 * - Does the transformation with the last lambda
 *
 * Source list is not modified. Target container is sorted by order of source container.
 *
 * converter takes additional parameter for existing Target object in case it exists in target collection.
 *
 */
fun <Source : HasId<Int>, Target : HasId<Int>> mergeDataInto(
    source: List<Source>,
    target: MutableList<Target>,
    converterFn: (Source, Target?) -> Target
) {
    // Existing data is collected for mapping
    val (targetIdentified, targetRest) = target.partition { it.id != null }
    val targetMap = targetIdentified.associateBy { it.id!! }

    // Target is overwritten with merged and new data from source
    target.clear()
    target.addAll(source.map { converterFn(it, targetMap[it.id]) })
    target.addAll(targetRest)
}

open class HankeServiceImpl(
    private val hankeRepository: HankeRepository,
    private val tormaystarkasteluService: TormaystarkasteluLaskentaService,
    private val hanketunnusService: HanketunnusService,
    private val geometriatService: GeometriatService,
    private val auditLogService: AuditLogService,
    private val hankeLoggingService: HankeLoggingService,
    private val applicationService: ApplicationService,
    private val permissionService: PermissionService,
    private val hankeKayttajaService: HankeKayttajaService,
) : HankeService {

    override fun getHankeId(hankeTunnus: String): Int? =
        hankeRepository.findByHankeTunnus(hankeTunnus)?.id

    /**
     * Hanke does not contain hakemukset. This function wraps Hanke and its hakemukset to a pair.
     */
    @Transactional(readOnly = true)
    override fun getHankeWithApplications(hankeTunnus: String): HankeWithApplications =
        hankeRepository.findByHankeTunnus(hankeTunnus).let { entity ->
            if (entity == null) {
                throw HankeNotFoundException(hankeTunnus)
            }
            return HankeWithApplications(
                createHankeDomainObjectFromEntity(entity),
                entity.hakemukset.map { hakemus -> hakemus.toApplication() }
            )
        }

    @Transactional(readOnly = true)
    override fun loadHanke(hankeTunnus: String) =
        hankeRepository.findByHankeTunnus(hankeTunnus)?.let {
            createHankeDomainObjectFromEntity(it)
        }

    @Transactional(readOnly = true)
    override fun loadAllHanke() =
        hankeRepository.findAll().map { createHankeDomainObjectFromEntity(it) }

    @Transactional(readOnly = true)
    override fun loadPublicHanke() =
        hankeRepository.findAllByStatus(HankeStatus.PUBLIC).map {
            createHankeDomainObjectFromEntity(it)
        }

    @Transactional(readOnly = true)
    override fun loadHankkeetByIds(ids: List<Int>) =
        hankeRepository.findAllById(ids).map { createHankeDomainObjectFromEntity(it) }

    @Transactional(readOnly = true)
    override fun loadHankkeetByUserId(userId: String) =
        hankeRepository.findAllByCreatedByUserIdOrModifiedByUserId(userId, userId).map {
            createHankeDomainObjectFromEntity(it)
        }

    /** @return a new Hanke instance with the added and possibly modified values. */
    @Transactional
    override fun createHanke(hanke: Hanke): Hanke {
        // TODO: Only create that hanke-tunnus if a specific set of fields are non-empty/set.

        val userId = currentUserId()

        val entity = HankeEntity()
        val loggingEntryHolder = prepareLogging(entity)

        // Create a new hanketunnus for it and save it:
        val hanketunnus = hanketunnusService.newHanketunnus()
        entity.hankeTunnus = hanketunnus
        hanke.hankeTunnus = hanketunnus

        // Copy values from the incoming domain object, and set some internal fields:
        copyNonNullHankeFieldsToEntity(hanke, entity)

        val existingYTs = prepareMapOfExistingYhteystietos(entity)
        copyYhteystietosToEntity(hanke, entity, userId, loggingEntryHolder, existingYTs)

        // NOTE: liikennehaittaindeksi and tormaystarkastelutulos are NOT
        //  copied from incoming data. Use setTormaystarkasteluTulos() for that.
        // NOTE: flags are NOT copied from incoming data, as they are set by internal logic.
        // Special fields; handled "manually"..
        entity.version = 0
        entity.createdByUserId = userId
        entity.createdAt = getCurrentTimeUTCAsLocalTime()
        entity.modifiedByUserId = null
        entity.modifiedAt = null

        calculateTormaystarkastelu(hanke, entity)
        entity.status = decideNewHankeStatus(entity)

        logger.debug { "Creating Hanke ${hanke.hankeTunnus}: ${hanke.toLogString()}" }
        val savedHankeEntity = hankeRepository.save(entity)
        logger.debug { "Created Hanke ${hanke.hankeTunnus}." }

        postProcessAndSaveLogging(loggingEntryHolder, savedHankeEntity, userId)

        return createHankeDomainObjectFromEntity(entity).also {
            hankeKayttajaService.saveNewTokensFromHanke(it)
            hankeLoggingService.logCreate(it, userId)
        }
    }

    /**
     * Create application when no existing hanke. Autogenerates hanke and applies application to it.
     */
    @Transactional
    override fun generateHankeWithApplication(
        cableReport: CableReportWithoutHanke,
        userId: String
    ): HankeWithApplications {
        val hanke = generateHankeFrom(cableReport)
        val tunnus = hanke.hankeTunnus ?: throw HankeArgumentException("Hanke must have tunnus")
        val createdApplication =
            applicationService.create(cableReport.toNewApplication(tunnus), userId)
        permissionService.setPermission(hanke.id!!, userId, Role.KAIKKI_OIKEUDET)
        return HankeWithApplications(hanke, listOf(createdApplication))
    }

    @Transactional
    override fun updateHanke(hanke: Hanke): Hanke {
        if (hanke.hankeTunnus == null) error("Somehow got here with hanke without hanke-tunnus")

        val userId = currentUserId()

        // Both checks that the hanke already exists, and get its old fields to transfer data into
        val entity =
            hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!)
                ?: throw HankeNotFoundException(hanke.hankeTunnus!!)

        val hankeBeforeUpdate = createHankeDomainObjectFromEntity(entity)

        val existingYTs = prepareMapOfExistingYhteystietos(entity)
        checkAndHandleDataProcessingRestrictions(hanke, entity, existingYTs, userId)

        val loggingEntryHolder = prepareLogging(entity)
        // Transfer field values from domain object to entity object, and set relevant audit fields:
        copyNonNullHankeFieldsToEntity(hanke, entity)
        copyYhteystietosToEntity(hanke, entity, userId, loggingEntryHolder, existingYTs)

        // NOTE: flags are NOT copied from incoming data, as they are set by internal logic.
        // Special fields; handled "manually"..
        entity.version = entity.version?.inc() ?: 1
        // (Not changing createdBy/At fields.)
        entity.modifiedByUserId = userId
        entity.modifiedAt = getCurrentTimeUTCAsLocalTime()
        entity.generated = false

        calculateTormaystarkastelu(hanke, entity)
        entity.status = decideNewHankeStatus(entity)

        // Save changes:
        logger.debug { "Saving Hanke ${hanke.hankeTunnus}: ${hanke.toLogString()}" }
        val savedHankeEntity = hankeRepository.save(entity)
        logger.debug { "Saved Hanke ${hanke.hankeTunnus}." }

        postProcessAndSaveLogging(loggingEntryHolder, savedHankeEntity, userId)

        return createHankeDomainObjectFromEntity(entity).also {
            hankeKayttajaService.saveNewTokensFromHanke(it)
            hankeLoggingService.logUpdate(hankeBeforeUpdate, it, userId)
        }
    }

    @Transactional
    override fun deleteHanke(hanke: Hanke, hakemukset: List<Application>, userId: String) {
        val hankeId = hanke.id ?: throw HankeArgumentException("Hanke must have an id")
        if (anyHakemusProcessingInAllu(hakemukset)) {
            throw HankeAlluConflictException(
                "Hanke ${hanke.hankeTunnus} has hakemus in Allu processing. Cannot delete."
            )
        }

        hakemukset.forEach { hakemus ->
            hakemus.id?.let { id -> applicationService.delete(id, userId) }
        }

        hankeRepository.deleteById(hankeId)
        hankeLoggingService.logDelete(hanke, userId)
    }

    private fun anyHakemusProcessingInAllu(hakemukset: List<Application>): Boolean =
        hakemukset.any {
            logger.info { "Hakemus ${it.id} has alluStatus ${it.alluStatus}" }
            !applicationService.isStillPending(it)
        }

    // TODO: functions to remove and invalidate Hanke's tormaystarkastelu-data
    //   At least invalidation can be done purely working on the particular
    //   tormaystarkasteluTulosEntity and -Repository.
    //   See TormaystarkasteluRepositoryITests for a way to remove.

    // ======================================================================

    private fun calculateTormaystarkastelu(source: Hanke, target: HankeEntity) {
        tormaystarkasteluService.calculateTormaystarkastelu(source)?.let {
            target.tormaystarkasteluTulokset.clear()
            target.tormaystarkasteluTulokset.add(
                TormaystarkasteluTulosEntity(
                    perus = it.perusIndeksi,
                    pyoraily = it.pyorailyIndeksi,
                    joukkoliikenne = it.joukkoliikenneIndeksi,
                    hanke = target
                )
            )
        }
    }

    private fun decideNewHankeStatus(entity: HankeEntity): HankeStatus {
        val validationResult =
            HankePublicValidator.validateHankeHasMandatoryFields(
                createHankeDomainObjectFromEntity(entity)
            )

        return when (val status = entity.status) {
            HankeStatus.DRAFT ->
                if (validationResult.isOk()) {
                    HankeStatus.PUBLIC
                } else {
                    logger.debug {
                        "A hanke draft wasn't ready to go public. hankeTunnus=${entity.hankeTunnus} failedFields=${validationResult.errorPaths().joinToString()}"
                    }
                    HankeStatus.DRAFT
                }
            HankeStatus.PUBLIC ->
                if (validationResult.isOk()) {
                    HankeStatus.PUBLIC
                } else {
                    logger.warn {
                        "A public hanke wasn't updated with missing or invalid fields. hankeTunnus=${entity.hankeTunnus} failedFields=${validationResult.errorPaths().joinToString()}"
                    }
                    throw HankeArgumentException(
                        "A public hanke didn't have all mandatory fields filled."
                    )
                }
            else ->
                throw HankeArgumentException(
                    "A hanke cannot be updated when in status $status. hankeTunnus=${entity.hankeTunnus}"
                )
        }
    }

    // --------------- Helpers for data transfer from database ------------
    private fun createHankealueDomainObjectFromEntity(hankealueEntity: HankealueEntity): Hankealue {
        return Hankealue(
            id = hankealueEntity.id,
            hankeId = hankealueEntity.hanke?.id,
            haittaAlkuPvm = hankealueEntity.haittaAlkuPvm?.atStartOfDay(TZ_UTC),
            haittaLoppuPvm = hankealueEntity.haittaLoppuPvm?.atStartOfDay(TZ_UTC),
            geometriat = hankealueEntity.geometriat?.let { geometriatService.getGeometriat(it) },
            kaistaHaitta = hankealueEntity.kaistaHaitta,
            kaistaPituusHaitta = hankealueEntity.kaistaPituusHaitta,
            meluHaitta = hankealueEntity.meluHaitta,
            polyHaitta = hankealueEntity.polyHaitta,
            tarinaHaitta = hankealueEntity.tarinaHaitta,
            nimi = hankealueEntity.nimi,
        )
    }

    private fun createHankeDomainObjectFromEntity(hankeEntity: HankeEntity): Hanke {
        val h =
            Hanke(
                hankeEntity.id,
                hankeEntity.hankeTunnus,
                hankeEntity.onYKTHanke,
                hankeEntity.nimi,
                hankeEntity.kuvaus,
                hankeEntity.vaihe,
                hankeEntity.suunnitteluVaihe,
                hankeEntity.version,
                // TODO: will need in future to actually fetch the username from another
                //   service.. (or whatever we choose to pass out here)
                //   Do it below, outside this construction call.
                hankeEntity.createdByUserId ?: "",
                // From UTC without timezone info to UTC with timezone info
                if (hankeEntity.createdAt != null) ZonedDateTime.of(hankeEntity.createdAt, TZ_UTC)
                else null,
                hankeEntity.modifiedByUserId,
                if (hankeEntity.modifiedAt != null) ZonedDateTime.of(hankeEntity.modifiedAt, TZ_UTC)
                else null,
                hankeEntity.status,
                hankeEntity.perustaja?.toDomainObject(),
                hankeEntity.generated,
            )

        h.tyomaaKatuosoite = hankeEntity.tyomaaKatuosoite
        h.tyomaaTyyppi = hankeEntity.tyomaaTyyppi

        createSeparateYhteystietoListsFromEntityData(h, hankeEntity)

        hankeEntity.listOfHankeAlueet.forEach {
            val alue = createHankealueDomainObjectFromEntity(it)
            alue.geometriat?.resetFeatureProperties(h)
            h.alueet.add(alue)
        }

        hankeEntity.tormaystarkasteluTulokset.firstOrNull()?.let {
            h.tormaystarkasteluTulos =
                TormaystarkasteluTulos(it.perus, it.pyoraily, it.joukkoliikenne)
        }

        return h
    }

    /**
     * createSeparateYhteystietoListsFromEntityData splits entity's one list to three different
     * contact information lists and adds them for Hanke domain object
     */
    private fun createSeparateYhteystietoListsFromEntityData(
        hanke: Hanke,
        hankeEntity: HankeEntity
    ) {

        hankeEntity.listOfHankeYhteystieto.forEach { hankeYhteystietoEntity ->
            val hankeYhteystieto =
                createHankeYhteystietoDomainObjectFromEntity(hankeYhteystietoEntity)

            when (hankeYhteystietoEntity.contactType) {
                OMISTAJA -> hanke.omistajat.add(hankeYhteystieto)
                TOTEUTTAJA -> hanke.toteuttajat.add(hankeYhteystieto)
                RAKENNUTTAJA -> hanke.rakennuttajat.add(hankeYhteystieto)
                MUU -> hanke.muut.add(hankeYhteystieto)
            }
        }
    }

    private fun createHankeYhteystietoDomainObjectFromEntity(
        hankeYhteystietoEntity: HankeYhteystietoEntity
    ): HankeYhteystieto {
        var createdAt: ZonedDateTime? = null

        if (hankeYhteystietoEntity.createdAt != null)
            createdAt = ZonedDateTime.of(hankeYhteystietoEntity.createdAt, TZ_UTC)

        var modifiedAt: ZonedDateTime? = null
        if (hankeYhteystietoEntity.modifiedAt != null)
            modifiedAt = ZonedDateTime.of(hankeYhteystietoEntity.modifiedAt, TZ_UTC)

        return with(hankeYhteystietoEntity) {
            HankeYhteystieto(
                id = id,
                nimi = nimi,
                email = email,
                puhelinnumero = puhelinnumero,
                organisaatioId = organisaatioId,
                organisaatioNimi = organisaatioNimi,
                osasto = osasto,
                createdBy = createdByUserId,
                modifiedBy = modifiedByUserId,
                createdAt = createdAt,
                modifiedAt = modifiedAt,
                alikontaktit = yhteyshenkilot,
                rooli = rooli,
                tyyppi = tyyppi,
            )
        }
    }

    // --------------- Helpers for data transfer towards database ------------

    /**
     * Checks if some to be changed or deleted yhteystieto has "datalocked" field set in the
     * currently persisted entry. If so, creates audit- and changelog-entries and throws an
     * exception. The exception prevents any other changes to the Hanke or other yhteystietos to be
     * saved. (The restricted yhteystietos would need to be left as is in order to make any other
     * changes to Hanke data). (NOTE: the current implementation may have insufficient feedback in
     * the UI about the situation.)
     *
     * The dataLocked field can be used e.g. to handle GDPR "personal data processing restriction"
     * requirement. It can be used for other "prevent changes" purposes, too, but current
     * implementation has been done with GDPR mostly in mind, so e.g. some messages/comments/log
     * entry info could be misleading for other uses.
     */
    private fun checkAndHandleDataProcessingRestrictions(
        incomingHanke: Hanke,
        persistedEntity: HankeEntity,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
        userid: String
    ) {
        logger.debug {
            "Checking for processing restrictions. Have ${existingYTs.size} existing Yhteystietos."
        }
        // Are there any locked yhteystietos? Very Likely not, so make that easy check first:
        val lockedExistingYTs: MutableMap<Int, HankeYhteystietoEntity> = mutableMapOf()
        existingYTs.values.forEach {
            if (it.dataLocked == true) {
                lockedExistingYTs[it.id!!] = it
            }
        }
        // If there are no existing locked yhteystietos, can proceed with the update:
        if (lockedExistingYTs.isEmpty()) {
            logger.debug {
                "Yhteystietos of the Hanke have no processing restrictions. Hanke update can continue."
            }
            return
        }

        logger.debug {
            "${lockedExistingYTs.size} of the existing Yhteystietos have processing restrictions. Checking for effects."
        }
        val loggingEntryHolderForRestrictedActions = prepareLogging(persistedEntity)
        // Some is locked, check incoming data and compare, looking for changes or deletions.
        // A temporary copy, so that we can remove handled entries from it, leaving
        // certain deleted entries as remainder:
        val tempLockedExistingYts = lockedExistingYTs.toMutableMap()
        listOf(
                incomingHanke.omistajat,
                incomingHanke.rakennuttajat,
                incomingHanke.toteuttajat,
                incomingHanke.muut
            )
            .forEach { yhteystiedot ->
                findAndLogAffectedBlockedYhteystietos(
                    yhteystiedot,
                    tempLockedExistingYts,
                    loggingEntryHolderForRestrictedActions,
                    userid
                )
            }

        // If no entries were blocked, and there is nothing left in the tempLockedExistingYts, all
        // clear to proceed:
        if (
            !loggingEntryHolderForRestrictedActions.hasEntries() && tempLockedExistingYts.isEmpty()
        ) {
            logger.debug {
                "No actual changes to the restricted Yhteystietos. Hanke update can continue."
            }
            return
        }

        // Any remaining entries in the tempLockedExistingYts means those would be deleted, if not
        // locked;
        // create audit log entries for them:
        tempLockedExistingYts.values.forEach {
            loggingEntryHolderForRestrictedActions.addLogEntriesForEvent(
                operation = Operation.DELETE,
                failed = true,
                failureDescription =
                    "delete hanke yhteystieto BLOCKED by data processing restriction",
                oldEntity = it,
                newEntity = null,
                userId = userid,
            )
        }
        logger.warn {
            "Hanke update with actions on processing restricted data, saving details to audit and change logs. Hanke id ${incomingHanke.id} will be left unchanged"
        }
        // This will throw exception (after saving the entries) and prevents
        // the Hanke update from continuing to actual changes to it.
        postProcessAndSaveLoggingForRestrictions(loggingEntryHolderForRestrictedActions)
    }

    private fun findAndLogAffectedBlockedYhteystietos(
        incomingYts: MutableList<HankeYhteystieto>,
        tempLockedExistingYts: MutableMap<Int, HankeYhteystietoEntity>,
        loggingEntryHolderForRestrictedActions: YhteystietoLoggingEntryHolder,
        userid: String
    ) {
        incomingYts.forEach { yhteystieto ->
            val lockedExistingYt = tempLockedExistingYts[yhteystieto.id]
            if (lockedExistingYt != null) {
                // If all main fields are empty, consider it as deletion;
                // otherwise check if any of the fields have changes compared to existing values.
                if (!yhteystieto.isAnyFieldSet()) {
                    loggingEntryHolderForRestrictedActions.addLogEntriesForEvent(
                        operation = Operation.DELETE,
                        failed = true,
                        failureDescription =
                            "delete hanke yhteystieto BLOCKED by data processing restriction",
                        oldEntity = tempLockedExistingYts[yhteystieto.id],
                        newEntity = null,
                        userId = userid,
                    )
                } else if (!areEqualIncomingVsExistingYhteystietos(yhteystieto, lockedExistingYt)) {
                    // copy the values to temporary non-persisted entity for logging:
                    val unsavedNewData = lockedExistingYt.cloneWithMainFields()
                    unsavedNewData.nimi = yhteystieto.nimi
                    unsavedNewData.email = yhteystieto.email
                    unsavedNewData.puhelinnumero = yhteystieto.puhelinnumero
                    unsavedNewData.organisaatioId = yhteystieto.organisaatioId
                    yhteystieto.organisaatioNimi?.let { unsavedNewData.organisaatioNimi = it }
                    yhteystieto.osasto?.let { unsavedNewData.osasto = it }
                    loggingEntryHolderForRestrictedActions.addLogEntriesForEvent(
                        operation = Operation.UPDATE,
                        failed = true,
                        failureDescription =
                            "update hanke yhteystieto BLOCKED by data processing restriction",
                        oldEntity = lockedExistingYt,
                        newEntity = unsavedNewData,
                        userId = userid,
                    )
                }
                tempLockedExistingYts.remove(yhteystieto.id!!)
            }
        }
    }

    /**
     * Does NOT copy the id and hankeTunnus fields because one is supposed to find the HankeEntity
     * instance from the database with those values, and after that, the values are filled by the
     * database and should not be changed. Also, version, createdByUserId, createdAt,
     * modifiedByUserId, modifiedAt, version are not set here, as they are to be set internally, and
     * depends on which operation is being done.
     */
    private fun copyNonNullHankeFieldsToEntity(hanke: Hanke, entity: HankeEntity) {
        hanke.onYKTHanke?.let { entity.onYKTHanke = hanke.onYKTHanke }
        hanke.nimi?.let { entity.nimi = hanke.nimi }
        hanke.kuvaus?.let { entity.kuvaus = hanke.kuvaus }

        hanke.perustaja?.let { entity.perustaja = it.toEntity() }
        entity.generated = hanke.generated
        hanke.vaihe?.let { entity.vaihe = hanke.vaihe }
        hanke.suunnitteluVaihe?.let { entity.suunnitteluVaihe = hanke.suunnitteluVaihe }
        hanke.tyomaaKatuosoite?.let { entity.tyomaaKatuosoite = hanke.tyomaaKatuosoite }
        entity.tyomaaTyyppi = hanke.tyomaaTyyppi

        // Merge hankealueet
        mergeDataInto(hanke.alueet, entity.listOfHankeAlueet) { source, target ->
            copyNonNullHankealueFieldsToEntity(hanke, source, target)
        }

        entity.listOfHankeAlueet.forEach { it.hanke = entity }
    }

    private fun copyNonNullHankealueFieldsToEntity(
        hanke: Hanke,
        source: Hankealue,
        target: HankealueEntity?
    ): HankealueEntity {
        val result = target ?: HankealueEntity()

        // Assuming the incoming date, while being zoned date and time, is in UTC and time value can
        // be simply dropped here.
        // Note, .toLocalDate() does not do any time zone conversion.
        source.haittaLoppuPvm?.let { result.haittaLoppuPvm = source.haittaLoppuPvm?.toLocalDate() }
        source.haittaAlkuPvm?.let { result.haittaAlkuPvm = source.haittaAlkuPvm?.toLocalDate() }

        source.kaistaHaitta?.let { result.kaistaHaitta = source.kaistaHaitta }
        source.kaistaPituusHaitta?.let { result.kaistaPituusHaitta = source.kaistaPituusHaitta }
        source.meluHaitta?.let { result.meluHaitta = source.meluHaitta }
        source.polyHaitta?.let { result.polyHaitta = source.polyHaitta }
        source.tarinaHaitta?.let { result.tarinaHaitta = source.tarinaHaitta }
        source.geometriat?.let {
            it.id = result.geometriat ?: it.id
            it.resetFeatureProperties(hanke)
            val saved = geometriatService.saveGeometriat(it)
            result.geometriat = saved?.id
        }
        source.nimi?.let { result.nimi = source.nimi }

        return result
    }

    /**
     * Creates a temporary id-to-existingyhteystieto -map. It can be used to find quickly whether an
     * incoming Yhteystieto is new or already in the database. (Also, copyYhteystietosToEntity() and
     * its subfunctions remove entries from such map as they process them, and any remaining entries
     * in it are considered as to be removed.) The given hankeEntity and its yhteystietos MUST be in
     * persisted state (have their id's set).
     */
    private fun prepareMapOfExistingYhteystietos(
        hankeEntity: HankeEntity
    ): MutableMap<Int, HankeYhteystietoEntity> {
        // Create temporary id-to-existingyhteystieto -map, used to find quickly whether an incoming
        // Yhteystieto is new or exists already.
        // YT = Yhteystieto
        val existingYTs: MutableMap<Int, HankeYhteystietoEntity> = mutableMapOf()
        for (existingYT in hankeEntity.listOfHankeYhteystieto) {
            val ytid = existingYT.id
            if (ytid == null) {
                throw DatabaseStateException(
                    "A persisted HankeYhteystietoEntity somehow missing id, Hanke id ${hankeEntity.id}"
                )
            } else {
                existingYTs[ytid] = existingYT
            }
        }
        return existingYTs
    }

    /**
     * Transfers yhteystieto fields from domain to (new or existing) entity object, combines the
     * three lists into one list, and sets the audit fields as relevant.
     */
    private fun copyYhteystietosToEntity(
        hanke: Hanke,
        entity: HankeEntity,
        userid: String,
        loggingEntryHolder: YhteystietoLoggingEntryHolder,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>
    ) {
        // Note, if the incoming data indicates it is an already saved yhteystieto (id-field is
        // set), should try to transfer the business fields to the same old corresponding entity.
        // Pretty much a must in order to preserve createdBy and createdAt field values without
        // having to rely on the client-side to hold the values for us (bad design), which would
        // also require checks on those (to prevent tampering).

        // Check each incoming yhteystieto (from three lists) for being new or an update to existing
        // one, and add to the main entity's single list if necessary:
        listOf(
                OMISTAJA to hanke.omistajat,
                RAKENNUTTAJA to hanke.rakennuttajat,
                TOTEUTTAJA to hanke.toteuttajat,
                MUU to hanke.muut
            )
            .forEach { (type, yhteystiedot) ->
                processIncomingHankeYhteystietosOfSpecificTypeToEntity(
                    yhteystiedot,
                    entity,
                    type,
                    userid,
                    existingYTs,
                    loggingEntryHolder
                )
            }

        // TODO: this method of removing entries if they are missing in the incoming data is
        //  different to behavior of the other simpler fields, where missing or null field is
        //  considered "keep the existing value, and return it back in response". However, those
        //  simpler fields can not be removed as a whole, so they _can_ behave so. For clarity,
        //  yhteystieto-entries should have separate operation for removal (but then they should
        //  also have separate operation for addition, i.e. own API endpoint in controller). So,
        //  consider the code below as "for now".
        //
        // TODO: Yhteystietos should not be in a list, but a "bag", or ensure it is e.g. linked list
        //  instead of arraylist (or similar). The order of Yhteystietos does not matter(?), and
        //  removing things from e.g. array list gets inefficient. Since there are so few entries,
        //  this crude solution works, for now.
        //
        // If there is anything left in the existingYTs map, they have been removed in the incoming
        // data, so remove them from the entity's list and make the back-reference null (and thus
        // delete from the database).
        for (hankeYht in existingYTs.values) {
            entity.removeYhteystieto(hankeYht)
            loggingEntryHolder.addLogEntriesForEvent(
                operation = Operation.DELETE,
                oldEntity = hankeYht,
                newEntity = null,
                userId = userid,
            )
        }
    }

    private fun processIncomingHankeYhteystietosOfSpecificTypeToEntity(
        listOfHankeYhteystiedot: List<HankeYhteystieto>,
        hankeEntity: HankeEntity,
        contactType: ContactType,
        userid: String,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
        loggingEntryHolder: YhteystietoLoggingEntryHolder
    ) {
        for (hankeYht in listOfHankeYhteystiedot) {
            val someFieldsSet = hankeYht.isAnyFieldSet()
            var validYhteystieto = false
            // TODO: yhteystieto validation is as of now a mess, due to multiple reasons.
            //   Just rethink it all everywhere before trying to implement something..
            //
            // The UI has currently bigger problems implementing it so that Yhteystieto entries that
            // haven't even been touched would not be sent to backend as a group of ""-fields, so
            // the condition here is to make such fully-empty entry considered as non-existent (i.e.
            // skip it).
            //
            // If any field is given (not empty and not only whitespace)...
            if (someFieldsSet) {
                validYhteystieto = hankeYht.isAnyFieldSet()
                // Check if at least the 4 mandatory fields are given
                // validYhteystieto = hankeYht.isValid()
            }

            // Is the incoming Yhteystieto new (does not have id, create new) or old (has id, update
            // existing)?
            if (hankeYht.id == null) {
                // New Yhteystieto
                // Note: don't need to (and can not) create audit-log entries during this create
                // processing; they are done later, after the whole hanke has been saved and new
                // yhteystietos got their db-ids.
                processCreateYhteystieto(
                    hankeYht,
                    validYhteystieto,
                    contactType,
                    userid,
                    hankeEntity
                )
            } else {
                // Should be an existing Yhteystieto
                processUpdateYhteystieto(
                    hankeYht,
                    existingYTs,
                    someFieldsSet,
                    validYhteystieto,
                    userid,
                    hankeEntity,
                    loggingEntryHolder
                )
            }
        }
    }

    private fun processCreateYhteystieto(
        hankeYht: HankeYhteystieto,
        validYhteystieto: Boolean,
        contactType: ContactType,
        userid: String,
        hankeEntity: HankeEntity
    ) {
        if (validYhteystieto) {
            // ... it is valid, so create a new Yhteystieto
            val hankeYhtEntity =
                HankeYhteystietoEntity(
                    contactType = contactType,
                    nimi = hankeYht.nimi,
                    email = hankeYht.email,
                    puhelinnumero = hankeYht.puhelinnumero,
                    organisaatioId = hankeYht.organisaatioId,
                    organisaatioNimi = hankeYht.organisaatioNimi,
                    osasto = hankeYht.osasto,
                    rooli = hankeYht.rooli,
                    tyyppi = hankeYht.tyyppi,
                    dataLocked = false,
                    dataLockInfo = null,
                    createdByUserId = userid,
                    createdAt = getCurrentTimeUTCAsLocalTime(),
                    modifiedByUserId = null,
                    modifiedAt = null,
                    id = null, // will be set by the database
                    yhteyshenkilot = hankeYht.alikontaktit,
                    hanke = hankeEntity // reference back to parent hanke
                )
            hankeEntity.addYhteystieto(hankeYhtEntity)
            // Logging of creating new yhteystietos is done after the hanke gets saved.
        } else {
            // ... missing some mandatory fields, should not have gotten here. Log it and skip it.
            logger.error {
                "Got a new Yhteystieto object with one or more empty mandatory fields, skipping it. HankeId ${hankeEntity.id}"
            }
        }
    }

    private fun processUpdateYhteystieto(
        hankeYht: HankeYhteystieto,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
        someFieldsSet: Boolean,
        validYhteystieto: Boolean,
        userid: String,
        hankeEntity: HankeEntity,
        loggingEntryHolder: YhteystietoLoggingEntryHolder
    ) {
        // If incoming Yhteystieto has id set, it _should_ be among the existing Yhteystietos, or
        // some kind of error has happened.
        val incomingId: Int = hankeYht.id!!
        val existingYT: HankeYhteystietoEntity? = existingYTs[incomingId]
        if (existingYT == null) {
            // Some sort of error situation;
            // - simultaneous edits to the same hanke by someone else (the Yhteystieto could have
            //   been removed in the database)
            // - the incoming ids are for different hanke (i.e. incorrect data in the incoming
            //   request)
            throw HankeYhteystietoNotFoundException(hankeEntity.id!!, incomingId)
        }

        if (validYhteystieto) {
            // Check if anything actually changed (UI can send all data as is on every request)
            if (!areEqualIncomingVsExistingYhteystietos(hankeYht, existingYT)) {
                // Record old data as JSON for change logging (and id for other purposes)
                val previousEntityData = existingYT.cloneWithMainFields()
                previousEntityData.id = existingYT.id

                // Update values in the persisted entity:
                existingYT.nimi = hankeYht.nimi
                existingYT.email = hankeYht.email
                existingYT.puhelinnumero = hankeYht.puhelinnumero
                existingYT.organisaatioId = hankeYht.organisaatioId
                existingYT.yhteyshenkilot = hankeYht.alikontaktit
                hankeYht.organisaatioNimi?.let {
                    existingYT.organisaatioNimi = hankeYht.organisaatioNimi
                }
                hankeYht.osasto?.let { existingYT.osasto = hankeYht.osasto }

                // (Not changing createdBy/At fields)
                existingYT.modifiedByUserId = userid
                existingYT.modifiedAt = getCurrentTimeUTCAsLocalTime()
                // (Not touching the id or hanke fields)

                loggingEntryHolder.addLogEntriesForEvent(
                    operation = Operation.UPDATE,
                    oldEntity = previousEntityData,
                    newEntity = existingYT,
                    userId = userid,
                )
            }

            // No need to add the existing Yhteystieto entity to the hanke's list; it is already in
            // it.
            //
            // Remove the corresponding entry from the map. (Afterwards, the entries remaining in
            // the map were not in the incoming data, so should be removed from the database.)
            existingYTs.remove(incomingId)
        } else {
            // Trying to update an Yhteystieto with one or more empty mandatory data fields. If we
            // do not change anything, the response will send back to previous stored values.
            // However, checking one special case; all fields being empty. This corresponds to
            // initial state, where the corresponding Yhteystieto is not set. Therefore, for now,
            // considering it as "please delete". (Handling it by reversed logic using the
            // existingYTs map, see the comment above in the do-update -case.)
            if (someFieldsSet) {
                logger.error {
                    "Got a new Yhteystieto object with one or more empty mandatory fields, skipping it. HankeId ${hankeEntity.id}"
                }
                existingYTs.remove(incomingId)
            }
            // If the entry was left in the existingYTs, it will get deleted.
        }
    }

    private fun areEqualIncomingVsExistingYhteystietos(
        incoming: HankeYhteystieto,
        existing: HankeYhteystietoEntity
    ): Boolean {
        if (incoming.nimi != existing.nimi) return false
        if (incoming.email != existing.email) return false
        if (incoming.puhelinnumero != existing.puhelinnumero) return false
        if (incoming.organisaatioId != existing.organisaatioId) return false
        if (incoming.organisaatioNimi != existing.organisaatioNimi) return false
        if (incoming.osasto != existing.osasto) return false
        if (incoming.alikontaktit != existing.yhteyshenkilot) return false
        return true
    }

    /**
     * Creates the entry holder object and initializes the old Yhteystieto id set (so that it can
     * know later which yhteystietos are created during the ongoing request).
     */
    private fun prepareLogging(entity: HankeEntity): YhteystietoLoggingEntryHolder {
        val loggingEntryHolder = YhteystietoLoggingEntryHolder()
        loggingEntryHolder.initWithOldYhteystietos(entity.listOfHankeYhteystieto)
        return loggingEntryHolder
    }

    /**
     * Handles post-processing of logging entries about restricted actions. Applies request's IP to
     * all given logging entries, saves them, and creates and throws an exception which indicates
     * that restricted yhteystietos can not be changed/deleted.
     */
    private fun postProcessAndSaveLoggingForRestrictions(
        loggingEntryHolderForRestrictedActions: YhteystietoLoggingEntryHolder
    ) {
        loggingEntryHolderForRestrictedActions.saveLogEntries(auditLogService)
        val idList = loggingEntryHolderForRestrictedActions.objectIds()
        throw HankeYhteystietoProcessingRestrictedException(
            "Can not modify/delete yhteystieto which has data processing restricted (id: $idList)"
        )
    }

    /**
     * Handles logging of all newly created Yhteystietos, applies request's IP to all log entries,
     * and saves all the log entries.
     *
     * Do not use this for the "restricted action" log events; see
     * [postProcessAndSaveLoggingForRestrictions] for that.
     */
    private fun postProcessAndSaveLogging(
        loggingEntryHolder: YhteystietoLoggingEntryHolder,
        savedHankeEntity: HankeEntity,
        userid: String
    ) {
        // It would be possible to process all operation types the same way afterwards like for
        // creating new yhteystietos, but that would cause some (relatively) minor extra work, and,
        // the proper solution would be to handle personal data access in its own separate service
        // and do the logging there independently... So, the current way of doing things should be
        // good enough for now.
        loggingEntryHolder.addLogEntriesForNewYhteystietos(
            savedHankeEntity.listOfHankeYhteystieto,
            userid
        )
        loggingEntryHolder.saveLogEntries(auditLogService)
    }

    /** Autogenerated hanke based on application data. Generated flag true. */
    private fun generateHankeFrom(cableReport: CableReportWithoutHanke): Hanke =
        createHanke(
            Hanke(
                id = null,
                hankeTunnus = null,
                onYKTHanke = null,
                nimi = cableReport.applicationData.name,
                kuvaus = null,
                vaihe = null,
                suunnitteluVaihe = null,
                version = null,
                createdBy = null,
                createdAt = null,
                modifiedBy = null,
                modifiedAt = null,
                HankeStatus.DRAFT,
                perustaja = null,
                generated = true,
            )
        )
}
