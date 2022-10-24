package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.TyomaaKoko
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import java.time.ZonedDateTime

object HankeFactory : Factory<Hanke>() {

    const val defaultHankeTunnus = "HAI21-1"
    const val defaultNimi = "Hämeentien perusparannus ja katuvalot"
    const val defaultId = 123
    const val defaultUser = "Risto"

    /**
     * Create a simple Hanke with test values. The default values can be overridden with named
     * parameters.
     *
     * Example:
     * ```
     * HankeFactory.create(id = null, hankeTunnus = null, nimi = "Testihanke")
     * ```
     */
    fun create(
        id: Int? = defaultId,
        hankeTunnus: String? = defaultHankeTunnus,
        nimi: String? = defaultNimi,
        alkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        loppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        vaihe: Vaihe? = Vaihe.OHJELMOINTI,
        suunnitteluVaihe: SuunnitteluVaihe? = null,
        version: Int? = 1,
        createdBy: String? = defaultUser,
        createdAt: ZonedDateTime? = getCurrentTimeUTC(),
    ): Hanke =
        Hanke(
            id,
            hankeTunnus,
            true,
            nimi,
            "lorem ipsum dolor sit amet...",
            alkuPvm,
            loppuPvm,
            vaihe,
            suunnitteluVaihe,
            version,
            createdBy,
            createdAt,
            null,
            null,
            SaveType.DRAFT
        )

    /**
     * Add a haitta to a test Hanke.
     *
     * Example:
     * ```
     * HankeFactory.create().withHaitta()
     * ```
     */
    fun Hanke.withHaitta(): Hanke {
        this.tyomaaKatuosoite = "Testikatu 1"
        this.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        this.tyomaaTyyppi.add(TyomaaTyyppi.MUU)
        this.tyomaaKoko = TyomaaKoko.LAAJA_TAI_USEA_KORTTELI
        this.haittaAlkuPvm = DateFactory.getStartDatetime()
        this.haittaLoppuPvm = DateFactory.getEndDatetime()
        this.kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI
        this.kaistaPituusHaitta = KaistajarjestelynPituus.NELJA
        this.meluHaitta = Haitta13.YKSI
        this.polyHaitta = Haitta13.KAKSI
        this.tarinaHaitta = Haitta13.KOLME
        return this
    }

    /**
     * Add yhteystiedot to a test Hanke. Generates the yhteystiedot with
     * [HankeYhteystietoFactory.createDifferentiated] using the given ints for differentiating the
     * yhteystiedot from each other.
     *
     * Without parameters, will add one contact for each role.
     *
     * You can provide a lambda for mutating all the generated yhteystieto after creation.
     *
     * Examples:
     * ```
     * HankeFactory.create().withYhteystiedot()
     *
     * HankeFactory.create().withYhteystiedot(
     *     omistajat = listOf(1,2),
     *     arvioijat = listOf(3,4),
     *     toteuttajat = listOf(2,5),
     * )
     * ```
     *
     * Using the withGeneratedX methods is probably cleaner than overriding the parameters of this
     * method.
     */
    fun Hanke.withYhteystiedot(
        omistajat: List<Int> = listOf(1),
        arvioijat: List<Int> = listOf(2),
        toteuttajat: List<Int> = listOf(3),
        mutator: (HankeYhteystieto) -> Unit = {},
    ): Hanke {
        this.omistajat.addAll(HankeYhteystietoFactory.createDifferentiated(omistajat, mutator))
        this.arvioijat.addAll(HankeYhteystietoFactory.createDifferentiated(arvioijat, mutator))
        this.toteuttajat.addAll(HankeYhteystietoFactory.createDifferentiated(toteuttajat, mutator))
        return this
    }

    /**
     * Add a number of omistaja to a hanke. Generates the yhteystiedot with
     * [HankeYhteystietoFactory.createDifferentiated] using the given ints for differentiating the
     * yhteystiedot from each other.
     *
     * Example:
     * ```
     * HankeFactory.create().withGeneratedOmistajat(listOf(1,2))
     * ```
     */
    fun Hanke.withGeneratedOmistajat(ids: List<Int>): Hanke {
        omistajat.addAll(HankeYhteystietoFactory.createDifferentiated(ids))
        return this
    }

    /**
     * Same as [Hanke.withGeneratedOmistajat] but using varargs instead of a list.
     *
     * Example:
     * ```
     * HankeFactory.create().withGeneratedOmistajat(1,2)
     * ```
     */
    fun Hanke.withGeneratedOmistajat(vararg ids: Int): Hanke = withGeneratedOmistajat(ids.toList())

    /**
     * Same as [Hanke.withGeneratedOmistajat] but adds a single omistaja.
     *
     * Example:
     * ```
     * HankeFactory.create().withGeneratedOmistaja(1)
     * ```
     */
    fun Hanke.withGeneratedOmistaja(id: Int): Hanke = withGeneratedOmistajat(id)

    /**
     * Add a number of arvioija to a hanke. Generates the yhteystiedot with
     * [HankeYhteystietoFactory.createDifferentiated] using the given ints for differentiating the
     * yhteystiedot from each other.
     *
     * Example:
     * ```
     * HankeFactory.create().withGeneratedArvioijat(listOf(1,2))
     * ```
     */
    fun Hanke.withGeneratedArvioijat(ids: List<Int>): Hanke {
        arvioijat.addAll(HankeYhteystietoFactory.createDifferentiated(ids))
        return this
    }

    /**
     * Same as [Hanke.withGeneratedArvioijat] but using varargs instead of a list.
     *
     * Example:
     * ```
     * HankeFactory.create().withGeneratedArvioijat(1,2)
     * ```
     */
    fun Hanke.withGeneratedArvioijat(vararg ids: Int): Hanke = withGeneratedArvioijat(ids.toList())

    /**
     * Same as [Hanke.withGeneratedArvioijat] but adds a single arvioija.
     *
     * Example:
     * ```
     * HankeFactory.create().withGeneratedArvioija(1)
     * ```
     */
    fun Hanke.withGeneratedArvioija(id: Int): Hanke = withGeneratedArvioijat(id)

    /**
     * Add a number of toteuttaja to a hanke. Generates the yhteystiedot with
     * [HankeYhteystietoFactory.createDifferentiated] using the given ints for differentiating the
     * yhteystiedot from each other.
     *
     * Example:
     * ```
     * HankeFactory.create().withGeneratedToteuttajat(listOf(1,2))
     * ```
     */
    fun Hanke.withGeneratedToteuttajat(ids: List<Int>): Hanke {
        toteuttajat.addAll(HankeYhteystietoFactory.createDifferentiated(ids))
        return this
    }

    /**
     * Same as [Hanke.withGeneratedToteuttajat] but using varargs instead of a list.
     *
     * Example:
     * ```
     * HankeFactory.create().withGeneratedToteuttajat(1,2)
     * ```
     */
    fun Hanke.withGeneratedToteuttajat(vararg ids: Int): Hanke =
        withGeneratedToteuttajat(ids.toList())

    /**
     * Same as [Hanke.withGeneratedToteuttajat] but adds a single toteuttaja.
     *
     * Example:
     * ```
     * HankeFactory.create().withGeneratedToteuttaja(1)
     * ```
     */
    fun Hanke.withGeneratedToteuttaja(id: Int): Hanke = withGeneratedToteuttajat(id)
}
