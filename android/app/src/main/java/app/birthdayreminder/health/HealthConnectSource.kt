package app.birthdayreminder.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.FitnessLog
import app.birthdayreminder.domain.HealthImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * 从 Health Connect（Android 官方开放的健康数据接口）读身体数据。
 * 手环、手表、体脂秤的 App（比如各品牌健康 App）只要把数据写进 Health Connect，这里就能读到。只读，不写。
 * Health Connect 的库要求 Android 8.0+，所以所有入口都先判断版本。
 */
object HealthConnectSource {
    private const val PROVIDER = "com.google.android.apps.healthdata"

    private val TYPES: List<KClass<out Record>> = listOf(
        HeartRateRecord::class, RestingHeartRateRecord::class, SleepSessionRecord::class, WeightRecord::class,
        HeightRecord::class, BodyFatRecord::class, BloodPressureRecord::class, OxygenSaturationRecord::class
    )

    /** 要申请的读取权限 */
    val PERMISSIONS: Set<String> get() = TYPES.map { HealthPermission.getReadPermission(it) }.toSet()

    enum class State { OK, NEED_INSTALL, UNSUPPORTED }

    fun state(ctx: Context): State {
        if (Build.VERSION.SDK_INT < 26) return State.UNSUPPORTED
        return when (HealthConnectClient.getSdkStatus(ctx, PROVIDER)) {
            HealthConnectClient.SDK_AVAILABLE -> State.OK
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> State.NEED_INSTALL
            else -> State.UNSUPPORTED
        }
    }

    /** 安卓 13 及以下要装 Health Connect App：打开应用商店 */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun granted(ctx: Context): Set<String> = try {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
    } catch (e: Exception) { emptySet() }

    /** 读一段时间里的数据（每种数据分页读完）；没有权限的那种数据读不到就跳过 */
    suspend fun read(ctx: Context, from: Instant, to: Instant): HealthImport.Raw = withContext(Dispatchers.IO) {
        val client = HealthConnectClient.getOrCreate(ctx)
        val range = TimeRangeFilter.between(from, to)
        suspend fun <T : Record> all(type: KClass<T>): List<T> = try {
            val out = ArrayList<T>()
            var token: String? = null
            var pages = 0
            do {
                val resp = client.readRecords(ReadRecordsRequest(type, range, pageToken = token))
                out.addAll(resp.records)
                token = resp.pageToken
                pages++
            } while (!token.isNullOrEmpty() && pages < 20)
            out
        } catch (e: Exception) { emptyList() }      // 这种数据没授权或读取失败：跳过

        HealthImport.Raw(
            heartRate = all(HeartRateRecord::class).flatMap { r -> r.samples.map { HealthImport.Sample(it.time.toEpochMilli(), it.beatsPerMinute.toDouble()) } },
            restingHr = all(RestingHeartRateRecord::class).map { HealthImport.Sample(it.time.toEpochMilli(), it.beatsPerMinute.toDouble()) },
            sleep = all(SleepSessionRecord::class).map { HealthImport.Span(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) },
            weightKg = all(WeightRecord::class).map { HealthImport.Sample(it.time.toEpochMilli(), it.weight.inKilograms) },
            heightM = all(HeightRecord::class).map { HealthImport.Sample(it.time.toEpochMilli(), it.height.inMeters) },
            bodyFat = all(BodyFatRecord::class).map { HealthImport.Sample(it.time.toEpochMilli(), it.percentage.value) },
            bloodPressure = all(BloodPressureRecord::class).map {
                HealthImport.Bp(it.time.toEpochMilli(), it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury)
            },
            spo2 = all(OxygenSaturationRecord::class).map { HealthImport.Sample(it.time.toEpochMilli(), it.percentage.value) }
        )
    }

    /** 某一天汇总好的身体数据（记录页“同步”用：先填进输入框，用户确认后再保存） */
    suspend fun readDay(ctx: Context, date: LocalDate): Map<String, Double> {
        val zone = ZoneId.systemDefault()
        val raw = read(ctx, date.minusDays(1).atTime(12, 0).atZone(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
        return HealthImport.daily(raw, zone)[date] ?: emptyMap()
    }

    data class Result(val days: Int, val filled: Int, val empty: Boolean)

    /**
     * 同步 from..to（含）这几天：读 Health Connect，按天汇总，只填空着的项（不覆盖手动填的）。
     * 睡眠从前一天中午开始读，免得漏掉跨夜的。
     */
    suspend fun import(ctx: Context, from: LocalDate, to: LocalDate): Result = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val raw = read(ctx, from.minusDays(1).atTime(12, 0).atZone(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
        if (raw.isEmpty) return@withContext Result(0, 0, true)
        val daily = HealthImport.daily(raw, zone).filterKeys { !it.isBefore(from) && !it.isAfter(to) }
        val dao = AppDatabase.get(ctx).fitnessDao()
        var days = 0; var filled = 0
        for ((d, metrics) in daily) {
            val old = dao.getByDate(d.toString())
            val (merged, n) = HealthImport.merge(old?.metrics ?: emptyMap(), metrics)
            if (n > 0) { dao.upsert(old?.copy(metrics = merged, updatedAt = System.currentTimeMillis()) ?: FitnessLog(d.toString(), merged)); days++; filled += n }
        }
        Result(days, filled, false)
    }
}
