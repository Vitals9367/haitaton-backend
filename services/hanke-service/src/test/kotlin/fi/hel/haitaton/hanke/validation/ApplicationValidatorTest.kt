package fi.hel.haitaton.hanke.validation

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEmpty
import assertk.assertions.isFailure
import assertk.assertions.isFalse
import assertk.assertions.isSuccess
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withApplicationData
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withCustomer
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withCustomerContacts
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withPostalAddress
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.touch
import java.time.ZonedDateTime
import java.util.stream.Stream
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class ApplicationValidatorTest {

    private val applicationValidator = ApplicationValidator()

    @Test
    fun `Correct application passes validation`() {
        val application = AlluDataFactory.createApplication()

        assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
    }

    @Nested
    inner class AtMostOneOrderer {
        private val customerWithOneOrderer =
            AlluDataFactory.createCompanyCustomer()
                .withContacts(
                    AlluDataFactory.createContact(orderer = true),
                    AlluDataFactory.createContact(orderer = false),
                )

        private val customerWithTwoOrderers =
            AlluDataFactory.createCompanyCustomer()
                .withContacts(
                    AlluDataFactory.createContact(orderer = true),
                    AlluDataFactory.createContact(orderer = true),
                )

        private val customerWithNoOrderers =
            AlluDataFactory.createCompanyCustomer()
                .withContacts(
                    AlluDataFactory.createContact(orderer = false),
                    AlluDataFactory.createContact(orderer = false),
                )

        private val customerWithNoContacts = AlluDataFactory.createCompanyCustomer().withContacts()

        @Test
        fun `One orderer is allowed`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        customerWithContacts = customerWithOneOrderer,
                    )

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `Zero orderers is allowed`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        customerWithContacts = customerWithNoOrderers,
                        contractorWithContacts = customerWithNoOrderers,
                    )

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `Two orderers throws exception`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        customerWithContacts = customerWithTwoOrderers,
                    )

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `Two orderers across different roles throws exception`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        customerWithContacts = customerWithOneOrderer,
                        contractorWithContacts = customerWithOneOrderer,
                    )

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `No contacts at all is allowed`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        customerWithContacts = customerWithNoContacts,
                        contractorWithContacts = customerWithNoContacts,
                    )
            val contacts =
                application.applicationData.customersWithContacts().flatMap { it.contacts }
            assertThat(contacts).isEmpty()

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CableReportApplicationDataValidate {

        private val baseAppData = AlluDataFactory.createCableReportApplicationData()

        private fun notJustWhitespaceCases(content: String): Stream<Arguments> =
            Stream.of(
                Arguments.of("name", baseAppData.copy(name = content)),
                Arguments.of("workDescription", baseAppData.copy(workDescription = content)),
                Arguments.of("customerReference", baseAppData.copy(customerReference = content)),
                Arguments.of(
                    "postalAddress.postalCode",
                    baseAppData.withPostalAddress(postalCode = content)
                ),
                Arguments.of("postalAddress.city", baseAppData.withPostalAddress(city = content)),
                Arguments.of(
                    "postalAddress.streetAddress.streetName",
                    baseAppData.withPostalAddress(streetAddress = content)
                ),
            )

        private fun justWhitespaceCases(): Stream<Arguments> = notJustWhitespaceCases(" ")

        private fun emptyCases(): Stream<Arguments> = notJustWhitespaceCases("")

        private fun textCases(): Stream<Arguments> = notJustWhitespaceCases("Some Text")

        @ParameterizedTest(name = "{0} should not be just whitespace")
        @MethodSource("justWhitespaceCases")
        fun `value should not be just whitespace`(
            case: String,
            applicationData: CableReportApplicationData
        ) {
            case.touch()
            val application = AlluDataFactory.createApplication(applicationData = applicationData)

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @ParameterizedTest(name = "{0} can be empty")
        @MethodSource("emptyCases")
        fun `value can be empty`(case: String, applicationData: CableReportApplicationData) {
            case.touch()
            val application = AlluDataFactory.createApplication(applicationData = applicationData)

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @ParameterizedTest(name = "{0} can have text with whitespaces")
        @MethodSource("textCases")
        fun `value can have text with whitespaces`(
            case: String,
            applicationData: CableReportApplicationData
        ) {
            case.touch()
            val application = AlluDataFactory.createApplication(applicationData = applicationData)

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `customerWithContacts is validated`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        customerWithContacts =
                            AlluDataFactory.createCompanyCustomer(name = " ").withContacts()
                    )

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }
        @Test
        fun `contractorWithContacts is validated`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        contractorWithContacts =
                            AlluDataFactory.createCompanyCustomer(name = " ").withContacts()
                    )

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `representativeWithContacts is validated when not null`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        representativeWithContacts =
                            AlluDataFactory.createCompanyCustomer(name = " ").withContacts()
                    )

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `representativeWithContacts can be null`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(representativeWithContacts = null)

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `propertyDeveloperWithContacts is validated when not null`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        propertyDeveloperWithContacts =
                            AlluDataFactory.createCompanyCustomer(name = " ").withContacts()
                    )

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `propertyDeveloperWithContacts can be null`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(propertyDeveloperWithContacts = null)

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `invoicingCustomer is validated when not null`() {
            val application =
                AlluDataFactory.createApplication(
                    applicationData =
                        AlluDataFactory.createCableReportApplicationData()
                            .copy(
                                invoicingCustomer =
                                    AlluDataFactory.createCompanyCustomer(name = " ")
                            )
                )

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `invoicingCustomer can be null`() {
            val application =
                AlluDataFactory.createApplication(
                    applicationData =
                        AlluDataFactory.createCableReportApplicationData()
                            .copy(invoicingCustomer = null)
                )

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }
    }

    @Nested
    inner class StartBeforeEnd {
        private val date: ZonedDateTime = ZonedDateTime.parse("2023-01-12T14:30:41Z")

        @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
        @CsvSource(",", ",2023-01-12T14:30:41Z", "2023-01-12T14:30:41Z,")
        fun `Null start and end dates are allowed`(
            startTime: ZonedDateTime?,
            endTime: ZonedDateTime?
        ) {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        startTime = startTime,
                        endTime = endTime,
                    )

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `Start date before end date is allowed`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        startTime = date,
                        endTime = date.plusDays(1),
                    )

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `Start date after end date throws exception`() {
            val application =
                AlluDataFactory.createApplication()
                    .withApplicationData(
                        startTime = date.plusDays(1),
                        endTime = date,
                    )

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CustomerValidate {
        private val baseCustomer = AlluDataFactory.createCompanyCustomer()

        private fun notJustWhitespaceCases(content: String): Stream<Arguments> =
            Stream.of(
                Arguments.of("name", baseCustomer.copy(name = content)),
                Arguments.of("email", baseCustomer.copy(email = content)),
                Arguments.of("phone", baseCustomer.copy(phone = content)),
                Arguments.of("ovt", baseCustomer.copy(ovt = content)),
                Arguments.of("invoicingOperator", baseCustomer.copy(invoicingOperator = content)),
                Arguments.of("sapCustomerNumber", baseCustomer.copy(sapCustomerNumber = content)),
            )

        private fun justWhitespaceCases(): Stream<Arguments> = notJustWhitespaceCases(" ")

        private fun emptyCases(): Stream<Arguments> = notJustWhitespaceCases("")

        private fun textCases(): Stream<Arguments> = notJustWhitespaceCases("Some Text")

        @ParameterizedTest(name = "{0} should not be just whitespace")
        @MethodSource("justWhitespaceCases")
        fun `value should not be just whitespace`(case: String, customer: Customer) {
            case.touch()
            val application =
                AlluDataFactory.createApplication().withCustomer(customer.withContacts())

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @ParameterizedTest(name = "{0} can be empty")
        @MethodSource("emptyCases")
        fun `value can be empty`(case: String, customer: Customer) {
            case.touch()
            val application =
                AlluDataFactory.createApplication().withCustomer(customer.withContacts())

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @ParameterizedTest(name = "{0} can have text with whitespaces")
        @MethodSource("textCases")
        fun `value can have text with whitespaces`(case: String, customer: Customer) {
            case.touch()
            val application =
                AlluDataFactory.createApplication().withCustomer(customer.withContacts())

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `country can't be free text`() {
            val application =
                AlluDataFactory.createApplication()
                    .withCustomer(baseCustomer.copy(country = "Some country").withContacts())

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `country can be two-letter country code`() {
            val application =
                AlluDataFactory.createApplication()
                    .withCustomer(baseCustomer.copy(country = "FI").withContacts())

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `country is case-sensitive`() {
            val application =
                AlluDataFactory.createApplication()
                    .withCustomer(baseCustomer.copy(country = "fi").withContacts())

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `country can't be three-letter country code`() {
            val application =
                AlluDataFactory.createApplication()
                    .withCustomer(baseCustomer.copy(country = "FIN").withContacts())

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `Valid business ID is allowed`() {
            val validBusinessId = "2182805-0"
            assertThat(validBusinessId.isValidBusinessId()).isTrue()
            val application =
                AlluDataFactory.createApplication()
                    .withCustomer(
                        AlluDataFactory.createCompanyCustomer(registryKey = validBusinessId)
                            .withContacts()
                    )

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @Test
        fun `Invalid business ID throws exception`() {
            val invalidBusinessId = "2182805-3"
            assertThat(invalidBusinessId.isValidBusinessId()).isFalse()
            val application =
                AlluDataFactory.createApplication()
                    .withCustomer(
                        AlluDataFactory.createCompanyCustomer(registryKey = invalidBusinessId)
                            .withContacts()
                    )

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ContactValidate {
        private val baseContact = AlluDataFactory.createContact()

        private fun notJustWhitespaceCases(content: String): Stream<Arguments> =
            Stream.of(
                Arguments.of("firstName", baseContact.copy(firstName = content)),
                Arguments.of("lastName", baseContact.copy(lastName = content)),
                Arguments.of("email", baseContact.copy(email = content)),
                Arguments.of("phone", baseContact.copy(phone = content)),
            )

        private fun justWhitespaceCases(): Stream<Arguments> = notJustWhitespaceCases(" ")

        private fun emptyCases(): Stream<Arguments> = notJustWhitespaceCases("")

        private fun textCases(): Stream<Arguments> = notJustWhitespaceCases("Some Text")

        @ParameterizedTest(name = "{0} should not be just whitespace")
        @MethodSource("justWhitespaceCases")
        fun `value should not be just whitespace`(case: String, contact: Contact) {
            case.touch()
            val application = AlluDataFactory.createApplication().withCustomerContacts(contact)

            assertThat { applicationValidator.isValid(application, null) }
                .isFailure()
                .hasClass(InvalidApplicationDataException::class)
        }

        @ParameterizedTest(name = "{0} can be empty")
        @MethodSource("emptyCases")
        fun `value can be empty`(case: String, contact: Contact) {
            case.touch()
            val application = AlluDataFactory.createApplication().withCustomerContacts(contact)

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }

        @ParameterizedTest(name = "{0} can have text with whitespaces")
        @MethodSource("textCases")
        fun `value can have text with whitespaces`(case: String, contact: Contact) {
            case.touch()
            val application = AlluDataFactory.createApplication().withCustomerContacts(contact)

            assertThat { applicationValidator.isValid(application, null) }.isSuccess().isTrue()
        }
    }
}
