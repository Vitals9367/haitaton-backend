package fi.hel.haitaton.hanke.allu

import assertk.Assert
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.application.ApplicationDecisionNotFoundException
import fi.hel.haitaton.hanke.configuration.Configuration.Companion.webClientWithLargeBuffer
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.getResourceAsBytes
import java.time.ZonedDateTime
import okhttp3.MultipartReader
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.geojson.Crs
import org.geojson.GeometryCollection
import org.geojson.LngLatAlt
import org.geojson.Polygon
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.http.MediaType.IMAGE_PNG
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class CableReportServiceAlluITests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: CableReportServiceAllu

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()

        val baseUrl = mockWebServer.url("/").toUrl().toString()
        val properties = AlluProperties(baseUrl, "fake_username", "any_password", 2)
        val webClient = webClientWithLargeBuffer(WebClient.builder())
        service = CableReportServiceAllu(webClient, properties)
    }

    @Test
    fun testCreate() {
        val stubbedBearer = addStubbedLoginResponse()
        val stubbedApplicationId = 1337
        val applicationIdResponse =
            MockResponse()
                .setResponseCode(200)
                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .setBody(stubbedApplicationId.toString())
        mockWebServer.enqueue(applicationIdResponse)
        val application = getTestApplication()

        val actualApplicationId = service.create(application)

        assertThat(actualApplicationId).isEqualTo(stubbedApplicationId)
        assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
        val request = mockWebServer.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v2/cablereports")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer $stubbedBearer")
    }

    @CsvSource("pdf,application/pdf", "png,image/png")
    @ParameterizedTest(name = "{displayName} ({arguments})")
    fun `addAttachment should upload attachment`(extension: String, contentType: String) {
        addStubbedLoginResponse()
        val alluId = 123
        val metadata =
            AlluDataFactory.createAttachmentMetadata(
                mimeType = contentType,
                name = "file.$extension"
            )
        val file = "test file content".toByteArray()
        val attachment = Attachment(metadata, file)
        val mockResponse = MockResponse().setResponseCode(200)
        mockWebServer.enqueue(mockResponse)

        service.addAttachment(alluId, attachment)

        assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
        val request = mockWebServer.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v2/applications/$alluId/attachments")
        assertThat(request.multiPartContentTypes())
            .containsExactly(APPLICATION_JSON_VALUE, contentType)
    }

    @Test
    fun `addAttachments should upload attachments successfully`() {
        addStubbedLoginResponse()
        val alluId = 123
        val file = "test file content".toByteArray()
        val attachment = AttachmentFactory.applicationAttachmentEntity(applicationId = 123456)
        val mockResponse = MockResponse().setResponseCode(200)
        (1..3).forEach { _ -> mockWebServer.enqueue(mockResponse) }
        val attachments = listOf(attachment, attachment, attachment)

        service.addAttachments(alluId, attachments) { _ -> file }

        assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
        attachments.forEach { _ ->
            val request = mockWebServer.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/v2/applications/$alluId/attachments")
        }
    }

    @Test
    fun testCreateErrorHandling() {
        val stubbedBearer = addStubbedLoginResponse()
        val applicationIdResponse =
            MockResponse()
                .setResponseCode(400)
                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .setBody(
                    """[{"errorMessage":"Cable report should have one orderer contact","additionalInfo":"customerWithContacts"}]"""
                )
        mockWebServer.enqueue(applicationIdResponse)

        assertThrows<WebClientResponseException.BadRequest> { service.create(getTestApplication()) }

        assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
        val createRequest = mockWebServer.takeRequest()
        assertThat(createRequest.method).isEqualTo("POST")
        assertThat(createRequest.path).isEqualTo("/v2/cablereports")
        assertThat(createRequest.getHeader("Authorization")).isEqualTo("Bearer $stubbedBearer")
    }

    @Nested
    inner class GetDecisionPdf {
        private val pdfBytes =
            "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()

        private fun pdfContent(): Buffer {
            val buffer = Buffer()
            buffer.write(pdfBytes)
            return buffer
        }

        @Test
        fun `returns PDF file as bytes`() {
            val stubbedBearer = addStubbedLoginResponse()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF_VALUE)
                    .setBody(pdfContent())
            )

            val response = service.getDecisionPdf(12)

            assertThat(response).isEqualTo(pdfBytes)
            assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
            val createRequest = mockWebServer.takeRequest()
            assertThat(createRequest.method).isEqualTo("GET")
            assertThat(createRequest.path).isEqualTo("/v2/cablereports/12/decision")
            assertThat(createRequest.getHeader("Authorization")).isEqualTo("Bearer $stubbedBearer")
        }

        @Test
        fun `returns big PDF file as bytes`() {
            addStubbedLoginResponse()
            val content = Buffer()
            repeat(1000000 / pdfBytes.size + 1) { content.write(pdfBytes) }
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF_VALUE)
                    .setBody(content)
            )

            val response = service.getDecisionPdf(12)

            assertThat(response).isEqualTo(content.readByteArray())
        }

        @Test
        fun `throws ApplicationDecisionNotFoundException on 404`() {
            addStubbedLoginResponse()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Not found")
            )

            val exception =
                assertThrows<ApplicationDecisionNotFoundException> { service.getDecisionPdf(12) }

            assertThat(exception).hasMessage("Decision not found in Allu. alluApplicationId=12")
        }

        @Test
        fun `throws WebClientResponseException on other error codes`() {
            addStubbedLoginResponse()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Other error")
            )

            assertThrows<WebClientResponseException> { service.getDecisionPdf(12) }
        }

        @Test
        fun `throws AlluApiException if the response does not have PDF Content-Type`() {
            addStubbedLoginResponse()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, IMAGE_PNG)
                    .setBody(pdfContent())
            )

            assertThrows<AlluApiException> { service.getDecisionPdf(12) }
        }

        @Test
        fun `throws AlluApiException if the response body is empty`() {
            addStubbedLoginResponse()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF)
                    .setBody("")
            )

            assertThrows<AlluApiException> { service.getDecisionPdf(12) }
        }
    }

    @Nested
    inner class GetApplicationStatusHistories {
        @Test
        fun `returns application histories`() {
            val alluids = listOf(12, 13)
            val eventsAfter = ZonedDateTime.parse("2022-10-10T15:25:34.981654Z")
            val histories = listOf(ApplicationHistoryFactory.create(applicationId = 12))
            val stubbedBearer = addStubbedLoginResponse()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody(OBJECT_MAPPER.writeValueAsString(histories))
            )

            val response = service.getApplicationStatusHistories(alluids, eventsAfter)

            assertThat(response).isEqualTo(histories)
            assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
            val createRequest = mockWebServer.takeRequest()
            assertThat(createRequest.method).isEqualTo("POST")
            assertThat(createRequest.path).isEqualTo("/v2/applicationhistory")
            assertThat(createRequest.getHeader("Authorization")).isEqualTo("Bearer $stubbedBearer")
        }

        @Test
        fun `doesn't panic on empty response`() {
            val alluids = listOf(12, 13)
            val eventsAfter = ZonedDateTime.parse("2022-10-10T15:25:34.981654Z")
            addStubbedLoginResponse()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("[]")
            )

            val response = service.getApplicationStatusHistories(alluids, eventsAfter)

            assertThat(response).isEqualTo(listOf())
        }

        @Test
        fun `throws error on bad status`() {
            val alluids = listOf(12, 13)
            val eventsAfter = ZonedDateTime.parse("2022-10-10T15:25:34.981654Z")
            addStubbedLoginResponse()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Error message")
            )

            assertThat { service.getApplicationStatusHistories(alluids, eventsAfter) }
                .isFailure()
                .hasClass(WebClientResponseException.NotFound::class)
        }
    }

    fun Assert<RecordedRequest>.isValidLoginRequest() = given { loginRequest ->
        prop(RecordedRequest::method).isEqualTo("POST")
        prop(RecordedRequest::path).isEqualTo("/v2/login")
        prop("Authorization header") { loginRequest.getHeader("Authorization") }.isNull()
    }

    private fun RecordedRequest.multiPartContentTypes(): List<String> {
        val boundary: String = headers[CONTENT_TYPE]?.split(";boundary=")?.last()!!
        val reader = MultipartReader(body, boundary)
        return generateSequence { reader.nextPart()?.headers?.get(CONTENT_TYPE) }.toList()
    }

    private fun addStubbedLoginResponse(): String {
        val stubbedBearer = "123dynamite-789wohoo"
        val loginResponse =
            MockResponse()
                .setResponseCode(200)
                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .setBody(stubbedBearer)
        mockWebServer.enqueue(loginResponse)
        return stubbedBearer
    }

    private fun getTestApplication(): AlluCableReportApplicationData {
        val customer =
            Customer(
                type = CustomerType.COMPANY,
                name = "Haitaton Oy Ab",
                country = "FI",
                email = "info@haitaton.fi",
                phone = "042-555-6125",
                registryKey = "101010-FAKE",
                ovt = null,
                invoicingOperator = null,
                sapCustomerNumber = null
            )
        val hannu =
            Contact(
                name = "Hannu Haitaton",
                email = "hannu@haitaton.fi",
                phone = "042-555-5216",
                orderer = true
            )
        val kerttu =
            Contact(
                name = "Kerttu Haitaton",
                email = "kerttu@haitaton.fi",
                phone = "042-555-2182",
                orderer = false
            )

        val customerWContacts = CustomerWithContacts(customer = customer, contacts = listOf(hannu))
        val contractorWContacts =
            CustomerWithContacts(customer = customer, contacts = listOf(kerttu))

        val geometry = GeometryCollection()
        geometry.add(
            Polygon(
                LngLatAlt(25495815.0, 6673160.0),
                LngLatAlt(25495855.0, 6673160.0),
                LngLatAlt(25495855.0, 6673190.0),
                LngLatAlt(25495815.0, 6673160.0)
            )
        )
        geometry.crs = Crs()
        geometry.crs.properties["name"] = "EPSG:3879"

        val now = ZonedDateTime.now()

        return AlluCableReportApplicationData(
            name = "Haitaton hankkeen nimi",
            customerWithContacts = customerWContacts,
            geometry = geometry,
            startTime = now.plusDays(1L),
            endTime = now.plusDays(22L),
            pendingOnClient = false,
            identificationNumber = "HAI-123",
            clientApplicationKind = "Telekaapelin laittoa",
            workDescription = "Kaivuhommiahan nää tietty",
            contractorWithContacts = contractorWContacts,
            constructionWork = true,
        )
    }
}
