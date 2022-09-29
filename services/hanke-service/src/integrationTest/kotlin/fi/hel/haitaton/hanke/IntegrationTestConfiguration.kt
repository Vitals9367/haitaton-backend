package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTormaysService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTormaysServicePG
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcOperations

@TestConfiguration
@Profile("itest")
class IntegrationTestConfiguration {

    @Bean fun jdbcOperations(): JdbcOperations = mockk()

    @Bean fun hankeRepository(): HankeRepository = mockk()

    @Bean fun auditLogRepository(): AuditLogRepository = mockk()

    @Bean fun hanketunnusService(): HanketunnusService = mockk()

    @Bean fun hankeService(): HankeService = mockk()

    @Bean fun permissionService(): PermissionService = mockk()

    @Bean fun organisaatioService(): OrganisaatioService = mockk()

    @Bean fun hankeGeometriatDao(jdbcOperations: JdbcOperations): HankeGeometriatDao = mockk()

    @Bean
    fun hankeGeometriatService(
        service: HankeService,
        hankeGeometriatDao: HankeGeometriatDao
    ): HankeGeometriatService = mockk()

    @Bean
    fun tormaysService(jdbcOperations: JdbcOperations): TormaystarkasteluTormaysService =
        TormaystarkasteluTormaysServicePG(jdbcOperations)

    @Bean fun tormaystarkasteluLaskentaService(): TormaystarkasteluLaskentaService = mockk()

    @EventListener
    fun onApplicationEvent(event: ContextRefreshedEvent) {
        // disable Sentry by mocking it
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any()) } returns SentryId()
    }
}
