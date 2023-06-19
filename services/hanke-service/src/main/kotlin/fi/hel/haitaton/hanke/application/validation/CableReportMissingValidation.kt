package fi.hel.haitaton.hanke.application.validation

import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.countOrderers
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.notBlank
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.notNullOrBlank
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateTrue
import java.util.Locale

/**
 * Validate required field are set. When application is a draft, it is ok to have fields that are
 * not yet defined. But e.g. when sending, they must be present.
 */
fun CableReportApplicationData.validateForMissing(): ValidationResult =
    validate { notBlank(name, "name") }
        .and { notBlank(workDescription, "workDescription") }
        .and { exactlyOneOrderer(customersWithContacts()) }
        .and { notNull(startTime, "startTime") }
        .and { notNull(endTime, "endTime") }
        .and { customerWithContacts.validateForMissing("customerWithContacts") }
        .and { notNull(areas, "areas") }
        .and { notNull(rockExcavation, "rockExcavation") }
        .and { contractorWithContacts.validateForMissing("contractorWithContacts") }
        .whenNotNull(representativeWithContacts) {
            it.validateForMissing("representativeWithContacts")
        }
        .whenNotNull(propertyDeveloperWithContacts) {
            it.validateForMissing("propertyDeveloperWithContacts")
        }

private fun CustomerWithContacts.validateForMissing(path: String): ValidationResult =
    customer
        .validateForMissing("$path.customer")
        .andAllIn(contacts, "$path.contacts", ::validateContactForMissing)

private fun Customer.validateForMissing(path: String): ValidationResult =
    validate { notNull(type, "$path.type") }
        .and { notBlank(name, "$path.name") }
        .and { validateTrue(Locale.getISOCountries().contains(country), "$path.country") }

private fun validateContactForMissing(contact: Contact, path: String) = validate {
    notNullOrBlank(contact.fullName(), "$path.firstName")
}

private fun exactlyOneOrderer(customersWithContacts: List<CustomerWithContacts>): ValidationResult =
    validateTrue(
        customersWithContacts.countOrderers() == 1,
        "customersWithContacts[].contacts[].orderer"
    )
