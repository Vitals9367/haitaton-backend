package fi.hel.haitaton.hanke.tormaystarkastelu

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.HankeEntity
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

data class TormaystarkasteluTulos(
    @JsonView(ChangeLogView::class) val perusIndeksi: Float,
    @JsonView(ChangeLogView::class) val pyorailyIndeksi: Float,
    @JsonView(ChangeLogView::class) val joukkoliikenneIndeksi: Float
) {

    @get:JsonView(ChangeLogView::class)
    val liikennehaittaIndeksi: LiikennehaittaIndeksiType by lazy {
        when (maxOf(perusIndeksi, pyorailyIndeksi, joukkoliikenneIndeksi)) {
            joukkoliikenneIndeksi ->
                LiikennehaittaIndeksiType(joukkoliikenneIndeksi, IndeksiType.JOUKKOLIIKENNEINDEKSI)
            perusIndeksi -> LiikennehaittaIndeksiType(perusIndeksi, IndeksiType.PERUSINDEKSI)
            else -> LiikennehaittaIndeksiType(pyorailyIndeksi, IndeksiType.PYORAILYINDEKSI)
        }
    }
}

data class LiikennehaittaIndeksiType(
    @JsonView(ChangeLogView::class) val indeksi: Float,
    @JsonView(ChangeLogView::class) val tyyppi: IndeksiType
)

enum class IndeksiType {
    PERUSINDEKSI,
    PYORAILYINDEKSI,
    JOUKKOLIIKENNEINDEKSI
}

@Entity
@Table(name = "tormaystarkastelutulos")
class TormaystarkasteluTulosEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int = 0,
    val perus: Float,
    val pyoraily: Float,
    val joukkoliikenne: Float,
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hankeid")
    val hanke: HankeEntity,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TormaystarkasteluTulosEntity) return false

        // For persisted entities, checking id match is enough
        if (id != other.id) return false

        // For non-persisted, have to compare almost everything
        if (hanke != other.hanke) return false
        if (perus != other.perus) return false
        if (pyoraily != other.pyoraily) return false
        if (joukkoliikenne != other.joukkoliikenne) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (hanke.hashCode())
        result = 31 * result + (perus.hashCode())
        result = 31 * result + (pyoraily.hashCode())
        result = 31 * result + (joukkoliikenne.hashCode())
        return result
    }
}
