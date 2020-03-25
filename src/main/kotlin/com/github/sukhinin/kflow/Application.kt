package com.github.sukhinin.kflow

import com.github.sukhinin.kflow.decoder.IpfixPacketDecoder
import com.github.sukhinin.kflow.metrics.MetricsConfigMapper
import com.github.sukhinin.kflow.server.Server
import com.github.sukhinin.kflow.server.ServerConfigMapper
import com.github.sukhinin.kflow.sink.KafkaFlowSink
import com.github.sukhinin.kflow.sink.KafkaFlowSinkConfigMapper
import com.github.sukhinin.micrometer.jmx.kafka.KafkaConsumerMetrics
import com.github.sukhinin.micrometer.jmx.kafka.KafkaProducerMetrics
import com.github.sukhinin.simpleconfig.*
import io.javalin.Javalin
import io.javalin.plugin.metrics.MicrometerPlugin
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.inf.ArgumentParserException
import net.sourceforge.argparse4j.inf.Namespace
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import kotlin.system.exitProcess

object Application {

    private val shutdownHooks: MutableList<Runnable> = ArrayList()
    private val logger = LoggerFactory.getLogger(Application::class.java)

    init {
        // Schedule running shutdown hooks on JVM shutdown
        Runtime.getRuntime().addShutdownHook(Thread(Application::runShutdownHooks))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val ns = parseCommandLineArgs(args)
        val config = getApplicationConfig(ns)

        val meterRegistry = createPrometheusMeterRegistry()
        setupCommonMeterBindings()

        try {
            logger.info("Initializing Prometheus metrics server")
            val metricsConfig = MetricsConfigMapper.from(config.scoped("metrics"))
            val javalin = createJavalinServer()
            javalin.get("/") { ctx -> ctx.result(meterRegistry.scrape()) }
            javalin.start(metricsConfig.port)
            shutdownHooks.add(Runnable { javalin.stop() })

            logger.info("Initializing Kafka sink")
            val sinkConfig = KafkaFlowSinkConfigMapper.from(config.scoped("kafka"))
            val sink = KafkaFlowSink(sinkConfig)
            shutdownHooks.add(Runnable { sink.close() })

            logger.info("Initializing IPFIX collector")
            val serverConfig = ServerConfigMapper.from(config.scoped("server"))
            val decoder = IpfixPacketDecoder()
            val server = Server(serverConfig, decoder, sink)
            server.start()
            shutdownHooks.add(Runnable { server.stop() })
        } catch (e: Exception) {
            // Javalin starts non-daemon thread so in case of an exception
            // force JVM to run shutdown hooks and exit
            logger.error("Error starting the application", e)
            exitProcess(2)
        }
    }

    private fun parseCommandLineArgs(args: Array<String>): Namespace {
        val parser = ArgumentParsers.newFor("kflow").build()
            .defaultHelp(true)
            .description("Prometheus remote write backend with Kafka export.")
        parser.addArgument("-c", "--config")
            .metavar("PATH")
            .help("path to configuration file")

        return try {
            parser.parseArgs(args)
        } catch (e: ArgumentParserException) {
            parser.handleError(e)
            exitProcess(1)
        }
    }

    private fun getApplicationConfig(ns: Namespace): Config {
        val systemPropertiesConfig = ConfigLoader.getConfigFromSystemProperties("app")
        val applicationConfig = ns.getString("config")?.let(ConfigLoader::getConfigFromPath) ?: MapConfig(emptyMap())
        val referenceConfig = ConfigLoader.getConfigFromSystemResource("reference.properties")

        val config = systemPropertiesConfig
            .withFallback(applicationConfig)
            .withFallback(referenceConfig)
            .resolved()
        logger.info("Loaded configuration:\n\t" + config.masked().dump().replace("\n", "\n\t"))

        return config
    }

    private fun createPrometheusMeterRegistry(): PrometheusMeterRegistry {
        val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        Metrics.addRegistry(meterRegistry)
        return meterRegistry
    }

    private fun setupCommonMeterBindings() {
        listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics(),
            LogbackMetrics(),
            KafkaProducerMetrics()
        ).forEach { binder -> binder.bindTo(Metrics.globalRegistry) }
    }

    private fun createJavalinServer(): Javalin {
        return Javalin.create { config ->
            config.registerPlugin(MicrometerPlugin())
            config.showJavalinBanner = false
            config.logIfServerNotStarted = false
        }
    }

    private fun runShutdownHooks() {
        // Run shutdown hooks in reverse registration order
        logger.info("Running registered application shutdown hooks")
        val reversedShutdownHooks = shutdownHooks.reversed()
        for (hook in reversedShutdownHooks) {
            try {
                hook.run()
            } catch (e: Exception) {
                logger.error("Exception in shutdown hook", e)
            }
        }
    }
}
