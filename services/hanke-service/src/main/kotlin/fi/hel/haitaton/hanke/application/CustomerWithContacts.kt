package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.allu.Contact as AlluContact
import fi.hel.haitaton.hanke.allu.Customer as AlluCustomer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.CustomerWithContacts as AlluCustomerWithContacts
import fi.hel.haitaton.hanke.allu.PostalAddress as AlluPostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress as AlluStreetAddress
import fi.hel.haitaton.hanke.domain.BusinessId

@JsonIgnoreProperties(ignoreUnknown = true)
data class CustomerWithContacts(val customer: Customer, val contacts: List<Contact>) {
    fun toAlluData(path: String): AlluCustomerWithContacts {
        return AlluCustomerWithContacts(
            customer.toAlluData("$path.customer"),
            contacts.map { it.toAlluData() }
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Contact(
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val phone: String?,
    val orderer: Boolean = false,
) {
    /** Check if this contact is blank, i.e. it doesn't contain any actual contact information. */
    @JsonIgnore fun isBlank() = listOf(firstName, lastName, email, phone).all { it.isNullOrBlank() }

    fun hasInformation() = !isBlank()

    fun toAlluData(): AlluContact = AlluContact(fullName(), email, phone, orderer)

    fun fullName(): String? {
        val names = listOf(firstName, lastName)
        if (names.all { it == null }) {
            return null
        }
        return names.filter { !it.isNullOrBlank() }.joinToString(" ")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class Customer(
    val type: CustomerType?, // Mandatory in Allu, but not in drafts.
    val name: String,
    val country: String, // ISO 3166-1 alpha-2 country code
    val email: String?,
    val phone: String?,
    val registryKey: BusinessId?, // y-tunnus
    val ovt: String?, // e-invoice identifier (ovt-tunnus)
    val invoicingOperator: String?, // e-invoicing operator code
    val sapCustomerNumber: String?, // customer's sap number
) {
    /** Check if this customer is blank, i.e. it doesn't contain any actual customer information. */
    @JsonIgnore
    fun isBlank() =
        name.isBlank() &&
            country.isBlank() &&
            email.isNullOrBlank() &&
            phone.isNullOrBlank() &&
            registryKey.isNullOrBlank() &&
            ovt.isNullOrBlank() &&
            invoicingOperator.isNullOrBlank() &&
            sapCustomerNumber.isNullOrBlank()

    fun hasInformation() = !isBlank()

    fun toAlluData(path: String): AlluCustomer =
        AlluCustomer(
            type ?: throw AlluDataException("$path.type", AlluDataError.NULL),
            name,
            country,
            email,
            phone,
            registryKey,
            ovt,
            invoicingOperator,
            sapCustomerNumber,
        )
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class PostalAddress(
    val streetAddress: StreetAddress,
    val postalCode: String,
    val city: String,
) {
    fun toAlluData(): AlluPostalAddress =
        AlluPostalAddress(streetAddress.toAlluData(), postalCode, city)
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class StreetAddress(val streetName: String?) {
    fun toAlluData(): AlluStreetAddress = AlluStreetAddress(streetName)
}
