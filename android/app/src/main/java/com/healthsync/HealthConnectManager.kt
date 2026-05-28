package com.healthsync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.lang.reflect.Method
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.reflect.KClass

data class HealthSnapshot(
    val deviceId: String,
    val recordedAt: String,
    val exportStart: String,
    val exportEnd: String,
    val grantedPermissions: List<String>,
    val requestedRecordTypes: List<String>,
    val steps: Long?,
    val caloriesActive: Long?,
    val caloriesTotal: Long?,
    val heartRateAvg: Int?,
    val heartRateResting: Int?,
    val distanceMeters: Long?,
    val activeMinutes: Long?,
    val sleepDurationMinutes: Long?,
    val sleepScore: Int?,
    val sleepStart: String?,
    val sleepEnd: String?,
    val sleepStartUtc: String?,
    val sleepEndUtc: String?,
    val sleepDate: String?,
    val sleepStages: Map<String, Long>?,
    val hrvRmssdAvgMs: Double?,
    val hrvRmssdMedianMs: Double?,
    val hrvRmssdMinMs: Double?,
    val hrvRmssdMaxMs: Double?,
    val hrvRmssdSampleCount: Int,
    val selectedSummaryOrigin: String?,
    val summaryDataOrigins: List<String>,
    val allSourcesSteps: Long?,
    val allSourcesDistanceMeters: Long?,
    val allSourcesCaloriesTotal: Long?,
    val rawRecords: Map<String, List<Map<String, Any?>>>,
    val extractionErrors: Map<String, String>
)

class HealthConnectManager(private val context: Context) {

    enum class Availability {
        AVAILABLE,
        INSTALL_OR_UPDATE_REQUIRED,
        UNAVAILABLE
    }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    private val supportedRecordTypes: List<KClass<out Record>> = listOf(
        ActiveCaloriesBurnedRecord::class,
        BasalBodyTemperatureRecord::class,
        BasalMetabolicRateRecord::class,
        BloodGlucoseRecord::class,
        BloodPressureRecord::class,
        BodyFatRecord::class,
        BodyTemperatureRecord::class,
        BodyWaterMassRecord::class,
        BoneMassRecord::class,
        CervicalMucusRecord::class,
        CyclingPedalingCadenceRecord::class,
        DistanceRecord::class,
        ElevationGainedRecord::class,
        ExerciseSessionRecord::class,
        FloorsClimbedRecord::class,
        HeartRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        HeightRecord::class,
        HydrationRecord::class,
        IntermenstrualBleedingRecord::class,
        LeanBodyMassRecord::class,
        MenstruationFlowRecord::class,
        MenstruationPeriodRecord::class,
        MindfulnessSessionRecord::class,
        NutritionRecord::class,
        OvulationTestRecord::class,
        OxygenSaturationRecord::class,
        PlannedExerciseSessionRecord::class,
        PowerRecord::class,
        RespiratoryRateRecord::class,
        RestingHeartRateRecord::class,
        SexualActivityRecord::class,
        SkinTemperatureRecord::class,
        SleepSessionRecord::class,
        SpeedRecord::class,
        StepsCadenceRecord::class,
        StepsRecord::class,
        TotalCaloriesBurnedRecord::class,
        Vo2MaxRecord::class,
        WeightRecord::class,
        WheelchairPushesRecord::class,
    )

    val permissions = supportedRecordTypes
        .map { HealthPermission.getReadPermission(it) }
        .toSet() + setOf(
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
        )

    private val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun availability(): Availability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Availability.INSTALL_OR_UPDATE_REQUIRED
            }
            else -> Availability.UNAVAILABLE
        }
    }

    fun installOrUpdateIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=com.google.android.apps.healthdata")
        ).apply {
            setPackage("com.android.vending")
        }
    }

    fun managePermissionsIntent(): Intent {
        return Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    suspend fun grantedPermissions(): Set<String> {
        if (availability() != Availability.AVAILABLE) return emptySet()
        return client.permissionController.getGrantedPermissions()
    }

    suspend fun hasPermissions(): Boolean {
        if (availability() != Availability.AVAILABLE) return false
        return grantedPermissions().containsAll(requiredPermissions)
    }

    suspend fun readTodaySnapshot(): HealthSnapshot {
        check(availability() == Availability.AVAILABLE) {
            "Health Connect is not available. Install or update Health Connect first."
        }

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val todayRange = TimeRangeFilter.between(startOfDay, now)
        val exportStart = now.minusSeconds(EXPORT_HISTORY_DAYS * 24 * 60 * 60)
        val exportRange = TimeRangeFilter.between(exportStart, now)

        // Sleep: look back 24h to catch last night
        val sleepRange = TimeRangeFilter.between(
            now.minusSeconds(86400),
            now
        )

        val sleep = readOptional { readSleep(sleepRange) }
        val hrv = readOptional { readHrvStats(sleepRange) }
        val summary = readOptional { readDailySummary(todayRange) }
        val export = readRawRecords(exportRange)

        return HealthSnapshot(
            deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ),
            recordedAt = ZonedDateTime.now().toString(),
            exportStart = exportStart.toString(),
            exportEnd = now.toString(),
            grantedPermissions = grantedPermissions().sorted(),
            requestedRecordTypes = supportedRecordTypes.map { it.java.simpleName }.sorted(),
            steps = summary?.steps,
            caloriesActive = summary?.caloriesActive,
            caloriesTotal = summary?.caloriesTotal,
            heartRateAvg = summary?.heartRateAvg,
            heartRateResting = summary?.heartRateResting,
            distanceMeters = summary?.distanceMeters,
            activeMinutes = summary?.exerciseMinutes,
            sleepDurationMinutes = summary?.sleepDurationMinutes ?: sleep?.durationMinutes,
            sleepScore = sleep?.score,
            sleepStart = sleep?.start,
            sleepEnd = sleep?.end,
            sleepStartUtc = sleep?.startUtc,
            sleepEndUtc = sleep?.endUtc,
            sleepDate = sleep?.sleepDate,
            sleepStages = sleep?.stages,
            hrvRmssdAvgMs = hrv?.averageMs,
            hrvRmssdMedianMs = hrv?.medianMs,
            hrvRmssdMinMs = hrv?.minMs,
            hrvRmssdMaxMs = hrv?.maxMs,
            hrvRmssdSampleCount = hrv?.sampleCount ?: 0,
            selectedSummaryOrigin = summary?.selectedOrigin,
            summaryDataOrigins = summary?.dataOrigins.orEmpty(),
            allSourcesSteps = summary?.allSourcesSteps,
            allSourcesDistanceMeters = summary?.allSourcesDistanceMeters,
            allSourcesCaloriesTotal = summary?.allSourcesCaloriesTotal,
            rawRecords = export.records,
            extractionErrors = export.errors
        )
    }

    private suspend fun <T> readOptional(block: suspend () -> T?): T? {
        return try {
            block()
        } catch (e: SecurityException) {
            null
        }
    }

    data class DailySummary(
        val steps: Long?,
        val caloriesActive: Long?,
        val caloriesTotal: Long?,
        val heartRateAvg: Int?,
        val heartRateResting: Int?,
        val distanceMeters: Long?,
        val exerciseMinutes: Long?,
        val sleepDurationMinutes: Long?,
        val selectedOrigin: String?,
        val dataOrigins: List<String>,
        val allSourcesSteps: Long?,
        val allSourcesDistanceMeters: Long?,
        val allSourcesCaloriesTotal: Long?
    )

    private suspend fun readDailySummary(range: TimeRangeFilter): DailySummary {
        val allSourcesResult = aggregateDailyMetrics(range, emptySet())
        val selectedOrigin = chooseSummaryOrigin(range, allSourcesResult.dataOrigins)
        val result = if (selectedOrigin != null) {
            aggregateDailyMetrics(range, setOf(DataOrigin(selectedOrigin)))
        } else {
            allSourcesResult
        }

        return DailySummary(
            steps = result[StepsRecord.COUNT_TOTAL],
            caloriesActive = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                ?.inKilocalories
                ?.toLong(),
            caloriesTotal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]
                ?.inKilocalories
                ?.toLong(),
            heartRateAvg = result[HeartRateRecord.BPM_AVG]?.toInt(),
            heartRateResting = result[RestingHeartRateRecord.BPM_AVG]?.toInt(),
            distanceMeters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.toLong(),
            exerciseMinutes = result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]
                ?.toMinutes(),
            sleepDurationMinutes = result[SleepSessionRecord.SLEEP_DURATION_TOTAL]
                ?.toMinutes(),
            selectedOrigin = selectedOrigin,
            dataOrigins = result.dataOrigins
                .map { it.packageName }
                .sorted(),
            allSourcesSteps = allSourcesResult[StepsRecord.COUNT_TOTAL],
            allSourcesDistanceMeters = allSourcesResult[DistanceRecord.DISTANCE_TOTAL]
                ?.inMeters
                ?.toLong(),
            allSourcesCaloriesTotal = allSourcesResult[TotalCaloriesBurnedRecord.ENERGY_TOTAL]
                ?.inKilocalories
                ?.toLong()
        )
    }

    private suspend fun aggregateDailyMetrics(
        range: TimeRangeFilter,
        dataOrigins: Set<DataOrigin>
    ) = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    HeartRateRecord.BPM_AVG,
                    RestingHeartRateRecord.BPM_AVG,
                    DistanceRecord.DISTANCE_TOTAL,
                    ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                    SleepSessionRecord.SLEEP_DURATION_TOTAL,
                ),
                timeRangeFilter = range,
                dataOriginFilter = dataOrigins,
            )
        )

    private suspend fun chooseSummaryOrigin(
        range: TimeRangeFilter,
        availableOrigins: Set<DataOrigin>
    ): String? {
        val availablePackages = availableOrigins.map { it.packageName }.toSet()
        val preferred = PREFERRED_DAILY_SUMMARY_ORIGINS.firstOrNull { it in availablePackages }
        if (preferred != null && originHasSteps(range, preferred)) return preferred

        val originsWithSteps = availablePackages.filter { originHasSteps(range, it) }
        return if (originsWithSteps.size == 1) originsWithSteps.first() else null
    }

    private suspend fun originHasSteps(range: TimeRangeFilter, packageName: String): Boolean {
        val result = aggregateDailyMetrics(range, setOf(DataOrigin(packageName)))
        return (result[StepsRecord.COUNT_TOTAL] ?: 0L) > 0L
    }

    data class RawExport(
        val records: Map<String, List<Map<String, Any?>>>,
        val errors: Map<String, String>
    )

    data class HrvStats(
        val averageMs: Double,
        val medianMs: Double,
        val minMs: Double,
        val maxMs: Double,
        val sampleCount: Int
    )

    private suspend fun readHrvStats(range: TimeRangeFilter): HrvStats? {
        val values = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = range,
                ascendingOrder = true,
            )
        ).records.map { it.heartRateVariabilityMillis }.sorted()

        if (values.isEmpty()) return null

        val middle = values.size / 2
        val median = if (values.size % 2 == 0) {
            (values[middle - 1] + values[middle]) / 2.0
        } else {
            values[middle]
        }

        return HrvStats(
            averageMs = roundOneDecimal(values.average()),
            medianMs = roundOneDecimal(median),
            minMs = roundOneDecimal(values.first()),
            maxMs = roundOneDecimal(values.last()),
            sampleCount = values.size
        )
    }

    private fun roundOneDecimal(value: Double): Double {
        return kotlin.math.round(value * 10.0) / 10.0
    }

    private suspend fun readRawRecords(range: TimeRangeFilter): RawExport {
        val recordsByType = linkedMapOf<String, List<Map<String, Any?>>>()
        val errorsByType = linkedMapOf<String, String>()

        for (recordType in supportedRecordTypes) {
            val name = recordType.java.simpleName
            try {
                val records = readRecordsUntyped(recordType, range)
                recordsByType[name] = records.map { recordToMap(it) }
            } catch (e: SecurityException) {
                recordsByType[name] = emptyList()
                errorsByType[name] = "Permission not granted"
            } catch (e: Exception) {
                recordsByType[name] = emptyList()
                errorsByType[name] = e.message ?: e.javaClass.simpleName
            }
        }

        return RawExport(recordsByType, errorsByType)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun readRecordsUntyped(
        recordType: KClass<out Record>,
        range: TimeRangeFilter
    ): List<Record> {
        val allRecords = mutableListOf<Record>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType as KClass<Record>,
                    timeRangeFilter = range,
                    ascendingOrder = true,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                )
            )
            allRecords += response.records
            pageToken = response.pageToken
        } while (pageToken != null && allRecords.size < MAX_RECORDS_PER_TYPE)

        return allRecords.take(MAX_RECORDS_PER_TYPE)
    }

    private fun recordToMap(record: Record): Map<String, Any?> {
        return linkedMapOf<String, Any?>(
            "record_type" to record.javaClass.simpleName,
        ) + objectToMap(record, 0)
    }

    private fun objectToMap(value: Any, depth: Int): Map<String, Any?> {
        val output = linkedMapOf<String, Any?>()
        value.javaClass.methods
            .asSequence()
            .filter { it.isPublicGetter() }
            .sortedBy { it.name }
            .forEach { method ->
                val name = method.propertyName()
                val propertyValue = try {
                    method.invoke(value)
                } catch (e: Exception) {
                    null
                }
                output[name] = toJsonSafeValue(propertyValue, depth + 1)
            }
        return output
    }

    private fun Method.isPublicGetter(): Boolean {
        if (parameterTypes.isNotEmpty()) return false
        if (name == "getClass") return false
        if (returnType == Void.TYPE) return false
        return name.startsWith("get") || name.startsWith("is")
    }

    private fun Method.propertyName(): String {
        val rawName = if (name.startsWith("get")) name.removePrefix("get") else name.removePrefix("is")
        return rawName.replaceFirstChar { it.lowercase() }
    }

    private fun toJsonSafeValue(value: Any?, depth: Int): Any? {
        if (value == null) return null
        if (depth > MAX_SERIALIZATION_DEPTH) return value.toString()

        return when (value) {
            is String, is Number, is Boolean -> value
            is Instant -> value.toString()
            is java.time.LocalDate -> value.toString()
            is java.time.LocalDateTime -> value.toString()
            is java.time.ZonedDateTime -> value.toString()
            is java.time.OffsetDateTime -> value.toString()
            is java.time.ZoneOffset -> value.toString()
            is Enum<*> -> value.name
            is Map<*, *> -> value.entries.associate { (key, entryValue) ->
                key.toString() to toJsonSafeValue(entryValue, depth + 1)
            }
            is Iterable<*> -> value.map { toJsonSafeValue(it, depth + 1) }
            else -> {
                val className = value.javaClass.name
                if (
                    className.startsWith("androidx.health.connect") ||
                    className.startsWith("androidx.health.platform")
                ) {
                    objectToMap(value, depth)
                } else {
                    value.toString()
                }
            }
        }
    }

    data class SleepData(
        val durationMinutes: Long,
        val score: Int?,
        val start: String,
        val end: String,
        val startUtc: String,
        val endUtc: String,
        val sleepDate: String,
        val stages: Map<String, Long>?
    )

    private suspend fun readSleep(range: TimeRangeFilter): SleepData? {
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = range,
                ascendingOrder = true,
            )
        ).records
        val session = records.maxByOrNull { it.endTime } ?: return null

        val durationMinutes = (session.endTime.toEpochMilli() - session.startTime.toEpochMilli()) / 60000
        val zone = ZoneId.systemDefault()
        val localStart = ZonedDateTime.ofInstant(session.startTime, zone)
        val localEnd = ZonedDateTime.ofInstant(session.endTime, zone)

        val stages = session.stages
            .groupBy { it.stage }
            .mapValues { (_, list) ->
                list.sumOf { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() } / 60000
            }
            .mapKeys { (stage, _) ->
                when (stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
                    SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
                    SleepSessionRecord.STAGE_TYPE_REM -> "rem"
                    SleepSessionRecord.STAGE_TYPE_AWAKE -> "awake"
                    else -> "unknown"
                }
            }

        return SleepData(
            durationMinutes = durationMinutes,
            score = null,
            start = localStart.toString(),
            end = localEnd.toString(),
            startUtc = session.startTime.toString(),
            endUtc = session.endTime.toString(),
            sleepDate = localEnd.toLocalDate().toString(),
            stages = stages.takeIf { it.isNotEmpty() }
        )
    }

    companion object {
        private const val EXPORT_HISTORY_DAYS = 30L
        private const val PAGE_SIZE = 500
        private const val MAX_RECORDS_PER_TYPE = 2_000
        private const val MAX_SERIALIZATION_DEPTH = 5
        private val PREFERRED_DAILY_SUMMARY_ORIGINS = listOf(
            "com.fitbit.FitbitMobile",
            "com.google.android.apps.fitness",
            "com.google.android.apps.healthdata",
        )
    }
}
