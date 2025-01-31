package fi.hel.haitaton.hanke.permissions

import assertk.Assert
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.matches
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import java.time.OffsetDateTime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val USERNAME = "test7358"
const val kayttajaTunnistePattern = "[a-zA-z0-9]{24}"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(USERNAME)
class HankeKayttajaServiceITest : DatabaseTest() {

    @Autowired private lateinit var hankeKayttajaService: HankeKayttajaService

    @Autowired private lateinit var hankeFactory: HankeFactory

    @Autowired private lateinit var kayttajaTunnisteRepository: KayttajaTunnisteRepository
    @Autowired private lateinit var hankeKayttajaRepository: HankeKayttajaRepository
    @Autowired private lateinit var permissionRepository: PermissionRepository
    @Autowired private lateinit var roleRepository: RoleRepository

    @Test
    fun `saveNewTokensFromApplication does nothing if application has no contacts`() {
        val hanke = hankeFactory.saveEntity()
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts = AlluDataFactory.createCompanyCustomer().withContacts(),
                contractorWithContacts = AlluDataFactory.createCompanyCustomer().withContacts()
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )

        hankeKayttajaService.saveNewTokensFromApplication(application, 1)

        assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
        assertThat(hankeKayttajaRepository.findAll()).isEmpty()
    }

    @Test
    fun `saveNewTokensFromApplication with different contact emails creates tokens for them all`() {
        val hanke = hankeFactory.saveEntity()
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                            AlluDataFactory.createContact(email = "email2")
                        ),
                contractorWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email3"),
                            AlluDataFactory.createContact(email = "email4")
                        )
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )

        hankeKayttajaService.saveNewTokensFromApplication(application, hanke.id!!)

        val tunnisteet = kayttajaTunnisteRepository.findAll()
        assertThat(tunnisteet).hasSize(4)
        assertThat(tunnisteet).areValid()
        val kayttajat = hankeKayttajaRepository.findAll()
        assertThat(kayttajat).hasSize(4)
        assertThat(kayttajat).each { kayttaja ->
            kayttaja.transform { it.nimi }.isEqualTo("Teppo Testihenkilö")
            kayttaja.transform { it.hankeId }.isEqualTo(hanke.id)
            kayttaja.transform { it.permission }.isNull()
            kayttaja.transform { it.kayttajaTunniste }.isNotNull()
        }
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
                "email3",
                "email4",
            )
    }

    @Test
    fun `saveNewTokensFromApplication with non-unique contact emails creates only the unique ones`() {
        val hanke = hankeFactory.saveEntity()
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                            AlluDataFactory.createContact(email = "email2"),
                            AlluDataFactory.createContact(
                                email = "email2",
                                firstName = "Other",
                                lastName = "Name"
                            ),
                        ),
                contractorWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                        )
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )

        hankeKayttajaService.saveNewTokensFromApplication(application, hanke.id!!)

        assertThat(kayttajaTunnisteRepository.findAll()).hasSize(2)
        val kayttajat = hankeKayttajaRepository.findAll()
        assertThat(kayttajat).hasSize(2)
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
            )
    }

    @Test
    fun `saveNewTokensFromApplication with pre-existing tokens creates only new ones`() {
        val hanke = hankeFactory.saveEntity()
        saveUserAndToken(hanke, "Existing User", "email1")
        saveUserAndToken(hanke, "Other User", "email4")
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                            AlluDataFactory.createContact(email = "email2")
                        ),
                contractorWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email3"),
                            AlluDataFactory.createContact(email = "email4")
                        )
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )
        assertThat(kayttajaTunnisteRepository.findAll()).hasSize(2)

        hankeKayttajaService.saveNewTokensFromApplication(application, hanke.id!!)

        assertThat(kayttajaTunnisteRepository.findAll()).hasSize(4)
        val kayttajat = hankeKayttajaRepository.findAll()
        assertThat(kayttajat).hasSize(4)
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
                "email3",
                "email4",
            )
    }

    @Test
    fun `saveNewTokensFromApplication with pre-existing permissions creates only new ones`() {
        val hanke = hankeFactory.saveEntity()
        saveUserAndPermission(hanke.id!!, "Existing User", "email1")
        saveUserAndPermission(hanke.id!!, "Other User", "email4")
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email1"),
                            AlluDataFactory.createContact(email = "email2")
                        ),
                contractorWithContacts =
                    AlluDataFactory.createCompanyCustomer()
                        .withContacts(
                            AlluDataFactory.createContact(email = "email3"),
                            AlluDataFactory.createContact(email = "email4")
                        )
            )
        val application =
            AlluDataFactory.createApplicationEntity(
                applicationData = applicationData,
                hanke = hanke
            )
        assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()

        hankeKayttajaService.saveNewTokensFromApplication(application, hanke.id!!)

        val tunnisteet = kayttajaTunnisteRepository.findAll()
        assertThat(tunnisteet).hasSize(2)
        assertThat(tunnisteet).each { tunniste ->
            tunniste.transform { it.hankeKayttaja?.sahkoposti }.isIn("email2", "email3")
        }
        val kayttajat = hankeKayttajaRepository.findAll()
        assertThat(kayttajat).hasSize(4)
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactlyInAnyOrder(
                "email1",
                "email2",
                "email3",
                "email4",
            )
    }

    @Test
    fun `saveNewTokensFromHanke does nothing if hanke has no contacts`() {
        hankeKayttajaService.saveNewTokensFromHanke(HankeFactory.create())

        assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
        assertThat(hankeKayttajaRepository.findAll()).isEmpty()
    }

    @Test
    fun `saveNewTokensFromHanke creates tokens for unique ones`() {
        val hanke =
            hankeFactory.save(
                HankeFactory.create()
                    .withYhteystiedot(
                        // each has a duplicate
                        omistajat = listOf(1, 1),
                        rakennuttajat = listOf(2, 2),
                        toteuttajat = listOf(3, 3),
                        muut = listOf(4, 4)
                    )
            )
        assertThat(hanke.extractYhteystiedot()).hasSize(8)

        hankeKayttajaService.saveNewTokensFromHanke(hanke)

        val tunnisteet: List<KayttajaTunnisteEntity> = kayttajaTunnisteRepository.findAll()
        val kayttajat: List<HankeKayttajaEntity> = hankeKayttajaRepository.findAll()
        assertThat(tunnisteet).hasSize(4) // 4 yhteyshenkilo subcontacts.
        assertThat(kayttajat).hasSize(4)
        assertThat(tunnisteet).areValid()
        assertThat(kayttajat).areValid(hanke.id)
    }

    @Test
    fun `saveNewTokensFromHanke with pre-existing permissions does not create duplicate`() {
        val hanke = hankeFactory.save()
        saveUserAndPermission(hanke.id!!, "Existing User One", "ali.kontakti@meili.com")

        hankeKayttajaService.saveNewTokensFromHanke(
            hanke.apply { this.omistajat.add(HankeYhteystietoFactory.create()) }
        )

        val tunnisteet = kayttajaTunnisteRepository.findAll()
        assertThat(tunnisteet).isEmpty()
        assertThat(tunnisteet).areValid()
        val kayttajat = hankeKayttajaRepository.findAll()
        assertThat(kayttajat).hasSize(1)
        assertThat(kayttajat.map { it.sahkoposti })
            .containsExactly(
                "ali.kontakti@meili.com",
            )
    }

    private fun Assert<List<KayttajaTunnisteEntity>>.areValid() = each { t ->
        t.transform { it.id }.isNotNull()
        t.transform { it.role }.isEqualTo(Role.KATSELUOIKEUS)
        t.transform { it.createdAt }.isRecent()
        t.transform { it.sentAt }.isNull()
        t.transform { it.tunniste }.matches(Regex(kayttajaTunnistePattern))
        t.transform { it.hankeKayttaja }.isNotNull()
    }

    private fun Assert<List<HankeKayttajaEntity>>.areValid(hankeId: Int?) = each { k ->
        k.transform { it.id }.isNotNull()
        k.transform { it.nimi }.isIn(*expectedNames)
        k.transform { it.sahkoposti }.isIn(*expectedEmails)
        k.transform { it.hankeId }.isEqualTo(hankeId)
        k.transform { it.permission }.isNull()
        k.transform { it.kayttajaTunniste }.isNotNull()
    }

    private val expectedNames =
        arrayOf(
            "yhteys-etu1 yhteys-suku1",
            "yhteys-etu2 yhteys-suku2",
            "yhteys-etu3 yhteys-suku3",
            "yhteys-etu4 yhteys-suku4",
        )

    private val expectedEmails =
        arrayOf(
            "yhteys-email1",
            "yhteys-email2",
            "yhteys-email3",
            "yhteys-email4",
        )

    private fun saveUserAndToken(
        hanke: HankeEntity,
        nimi: String,
        sahkoposti: String
    ): HankeKayttajaEntity {
        val kayttajaTunnisteEntity =
            kayttajaTunnisteRepository.save(
                KayttajaTunnisteEntity(
                    tunniste = "existing",
                    createdAt = OffsetDateTime.parse("2023-03-31T15:41:21Z"),
                    sentAt = null,
                    role = Role.KATSELUOIKEUS,
                    hankeKayttaja = null,
                )
            )
        return hankeKayttajaRepository.save(
            HankeKayttajaEntity(
                hankeId = hanke.id!!,
                nimi = nimi,
                sahkoposti = sahkoposti,
                permission = null,
                kayttajaTunniste = kayttajaTunnisteEntity,
            )
        )
    }

    private fun saveUserAndPermission(
        hankeId: Int,
        nimi: String,
        sahkoposti: String
    ): HankeKayttajaEntity {
        val permissionEntity =
            permissionRepository.save(
                PermissionEntity(
                    userId = "fake id",
                    hankeId = hankeId,
                    role = roleRepository.findOneByRole(Role.KATSELUOIKEUS),
                )
            )

        return hankeKayttajaRepository.save(
            HankeKayttajaEntity(
                hankeId = hankeId,
                nimi = nimi,
                sahkoposti = sahkoposti,
                permission = permissionEntity,
                kayttajaTunniste = null,
            )
        )
    }
}
