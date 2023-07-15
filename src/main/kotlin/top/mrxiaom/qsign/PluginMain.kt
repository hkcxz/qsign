package top.mrxiaom.qsign

import com.tencent.mobileqq.dt.model.FEBound
import kotlinx.serialization.json.*
import moe.fuqiuluo.comm.QSignConfig
import moe.fuqiuluo.comm.checkIllegal
import net.mamoe.mirai.console.extension.PluginComponentStorage
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.plugin.version
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.hexToBytes
import net.mamoe.mirai.utils.toUHexString
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object PluginMain : KotlinPlugin(
    JvmPluginDescriptionBuilder(
        "top.mrxiaom.qsign", BuildConstants.VERSION
    ).apply {
        name("QSign")
        author("MrXiaoM")
    }.build()
) {
    lateinit var basePath: File
    lateinit var CONFIG: QSignConfig
    override fun PluginComponentStorage.onLoad() {
        PluginConfig.reload()
        basePath = File(PluginConfig.basePath)
        logger.info("Loading QSign v$version")
        logger.info("运行目录: ${basePath.absolutePath}")

        FEBound.initAssertConfig(basePath)
        CONFIG = Json.decodeFromString(
            QSignConfig.serializer(),
            basePath.resolve("config.json").readText()
        ).apply { checkIllegal() }

        logger.info("已成功读取签名服务配置")
        logger.info("  签名服务版本: ${CONFIG.protocol.version}")
        logger.info("  签名服务QUA: ${CONFIG.protocol.qua}")
        logger.info("=============================================")

        for (protocol in BotConfiguration.MiraiProtocol.values()) {
            val file = basePath.resolve("$protocol.json")
            if (file.exists()) {
                kotlin.runCatching {
                    val json = Json.parseToJsonElement(file.readText()).jsonObject
                    protocol.applyProtocolInfo(json)
                    logger.info("已加载 $protocol 协议变更: ${protocol.status()}")
                }.onFailure {
                    logger.warning("加载 $protocol 的协议变更时发生一个异常", it)
                }
            }
        }

        QSignService.cmdWhiteList = getResource("cmd_whitelist.txt")?.lines() ?: error("`cmd_whitelist.txt` not found.")

        QSignService.Factory.register()
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private fun BotConfiguration.MiraiProtocol.applyProtocolInfo(json: JsonObject) {
    net.mamoe.mirai.internal.utils.MiraiProtocolInternal.protocols.compute(this) { _, impl ->
        impl?.apply {
                apkId = json.getValue("apk_id").jsonPrimitive.content
                id = json.getValue("app_id").jsonPrimitive.long
                buildVer = json.getValue("sort_version_name").jsonPrimitive.content
                ver = buildVer.substringBeforeLast(".")
                sdkVer = json.getValue("sdk_version").jsonPrimitive.content
                miscBitMap = json.getValue("misc_bitmap").jsonPrimitive.int
                subSigMap = json.getValue("sub_sig_map").jsonPrimitive.int
                mainSigMap = json.getValue("main_sig_map").jsonPrimitive.int
                sign = json.getValue("apk_sign").jsonPrimitive.content.hexToBytes().toUHexString(" ")
                buildTime = json.getValue("build_time").jsonPrimitive.long
                ssoVersion = json.getValue("sso_version").jsonPrimitive.int
                appKey = json.getValue("app_key").jsonPrimitive.content
        }
    }
}
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private fun BotConfiguration.MiraiProtocol.status(): String {
    val impl = net.mamoe.mirai.internal.utils.MiraiProtocolInternal.protocols[this] ?: return "INVALID PROTOCOL"
    val buildTime = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss").format(Date(impl.buildTime * 1000L))
    return "${impl.buildVer} ($buildTime)"
}