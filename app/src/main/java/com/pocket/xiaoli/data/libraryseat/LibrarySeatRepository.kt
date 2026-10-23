package com.pocket.xiaoli.data.libraryseat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

data class LibrarySeatUser(
    val name: String?,
    val school: String?,
    val studentNo: String?,
)

data class LibrarySeatReservation(
    val status: Int,
    val libraryName: String?,
    val libraryFloor: String?,
    val seatName: String?,
    val date: String?,
    val expDate: String?,
    val diff: String?,
    val holdDate: String?,
)

data class LibrarySeatSession(
    val user: LibrarySeatUser?,
    val reservation: LibrarySeatReservation?,
    val sToken: String?,
)

data class LibrarySeatLibrary(
    val id: Int,
    val name: String,
    val floor: String?,
    val isOpen: Boolean,
)

data class LibrarySeatSeat(
    val x: Int,
    val y: Int,
    val key: String,
    val type: Int,
    val name: String?,
    val seatStatus: Int,
    val occupied: Boolean,
)

data class LibrarySeatLayout(
    val maxX: Int,
    val maxY: Int,
    val seats: List<LibrarySeatSeat>,
)

class TraceIntException(
    val code: Int,
    message: String,
) : Exception(message)

/**
 * 「我去图书馆」座位预约 GraphQL 客户端。
 *
 * 登录态由 H5 登录页建立，App 通过 CookieManager 复用 traceint.com 域下的 Cookie，
 * 然后用 OkHttp 调 GraphQL。所有接口均来自该 H5 公开页面自身使用的 GraphQL operation。
 */
object LibrarySeatRepository {

    private const val API_URL = "https://wechat.v2.traceint.com/index.php/graphql/"
    private const val APP_VERSION = "1.0"

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val queryIndex = """
        query index(${'$'}pos: String!, ${'$'}param: [hash]) {
          userAuth {
            currentUser {
              user_id
              user_nick
              user_sch
              user_student_name
            }
            reserve {
              reserve {
                status
                lib_id
                lib_name
                lib_floor
                seat_name
                date
                exp_date_str
                diff_str
                hold_date
              }
              getSToken
            }
          }
        }
    """.trimIndent()

    private const val MUTATION_HOLD =
        "mutation reserveHold { userAuth { reserve { reserveHold } } }"

    private const val MUTATION_HOLD_CONFIRM =
        "mutation reserveHoldConfirm { userAuth { reserve { reserveHoldConfirm } } }"

    private const val MUTATION_CANCEL = """
        mutation reserveCancle(${'$'}sToken: String!) {
          userAuth {
            reserve {
              reserveCancle(sToken: ${'$'}sToken) {
                timerange
                img
                hours
                mins
                per
              }
            }
          }
        }
    """

    private const val QUERY_LIBS = """
        query list {
          userAuth {
            reserve {
              libs(libType: -1) {
                lib_id
                lib_name
                lib_floor
                is_open
              }
            }
          }
        }
    """

    private const val QUERY_LAYOUT = """
        query libLayout(${'$'}libId: Int, ${'$'}libType: Int) {
          userAuth {
            reserve {
              libs(libType: ${'$'}libType, libId: ${'$'}libId) {
                lib_layout {
                  max_x
                  max_y
                  seats {
                    x
                    y
                    key
                    type
                    name
                    seat_status
                    status
                  }
                }
              }
            }
          }
        }
    """

    private const val MUTATION_RESERVE_SEAT = """
        mutation reserueSeat(${'$'}libId: Int!, ${'$'}seatKey: String!, ${'$'}captchaCode: String, ${'$'}captcha: String!) {
          userAuth {
            reserve {
              reserueSeat(
                libId: ${'$'}libId
                seatKey: ${'$'}seatKey
                captchaCode: ${'$'}captchaCode
                captcha: ${'$'}captcha
              )
            }
          }
        }
    """

    suspend fun fetchSession(cookie: String): Result<LibrarySeatSession> =
        request(cookie, "index", queryIndex, mapOf("pos" to "", "param" to emptyList<Any>()))
            .map { data ->
                val auth = data.getAsJsonObject("userAuth")
                    ?: return@map LibrarySeatSession(null, null, null)
                val currentUser = auth.obj("currentUser")
                val reserveNode = auth.obj("reserve")
                val reserve = reserveNode?.obj("reserve")
                LibrarySeatSession(
                    user = currentUser?.let {
                        LibrarySeatUser(
                            name = it.str("user_nick"),
                            school = it.str("user_sch"),
                            studentNo = it.str("user_student_name"),
                        )
                    },
                    reservation = reserve?.let {
                        LibrarySeatReservation(
                            status = it.int("status") ?: 0,
                            libraryName = it.str("lib_name"),
                            libraryFloor = it.str("lib_floor"),
                            seatName = it.str("seat_name"),
                            date = it.str("date"),
                            expDate = it.str("exp_date_str"),
                            diff = it.str("diff_str"),
                            holdDate = it.str("hold_date"),
                        )
                    },
                    sToken = reserveNode?.str("getSToken"),
                )
            }

    suspend fun hold(cookie: String): Result<Unit> =
        request(cookie, "reserveHold", MUTATION_HOLD, emptyMap())
            .map { }

    suspend fun holdConfirm(cookie: String): Result<Unit> =
        request(cookie, "reserveHoldConfirm", MUTATION_HOLD_CONFIRM, emptyMap())
            .map { }

    suspend fun cancel(cookie: String, sToken: String): Result<Unit> =
        request(cookie, "reserveCancle", MUTATION_CANCEL, mapOf("sToken" to sToken))
            .map { }

    suspend fun fetchLibraries(cookie: String): Result<List<LibrarySeatLibrary>> =
        request(cookie, "list", QUERY_LIBS, emptyMap())
            .map { data ->
                val array = data.getAsJsonObject("userAuth")
                    ?.getAsJsonObject("reserve")
                    ?.getAsJsonArray("libs")
                    ?: return@map emptyList()
                array.mapNotNull { item ->
                    val obj = item.asJsonObject
                    val id = obj.int("lib_id") ?: return@mapNotNull null
                    LibrarySeatLibrary(
                        id = id,
                        name = obj.str("lib_name").orEmpty(),
                        floor = obj.str("lib_floor"),
                        isOpen = obj.get("is_open")?.asBoolean ?: true,
                    )
                }
            }

    suspend fun fetchLayout(cookie: String, libId: Int): Result<LibrarySeatLayout> =
        request(
            cookie = cookie,
            operationName = "libLayout",
            query = QUERY_LAYOUT,
            variables = mapOf("libId" to libId, "libType" to -1),
        ).map { data ->
            val libs = data.getAsJsonObject("userAuth")
                ?.getAsJsonObject("reserve")
                ?.getAsJsonArray("libs")
                ?: return@map LibrarySeatLayout(0, 0, emptyList())
            val layout = libs.firstOrNull()?.asJsonObject?.obj("lib_layout")
                ?: return@map LibrarySeatLayout(0, 0, emptyList())
            val seats = layout.getAsJsonArray("seats")?.mapNotNull { item ->
                val obj = item.asJsonObject
                val key = obj.str("key") ?: return@mapNotNull null
                LibrarySeatSeat(
                    x = obj.int("x") ?: 0,
                    y = obj.int("y") ?: 0,
                    key = key,
                    type = obj.int("type") ?: 0,
                    name = obj.str("name"),
                    seatStatus = obj.int("seat_status") ?: 0,
                    occupied = obj.get("status")?.asBoolean ?: false,
                )
            }.orEmpty()
            LibrarySeatLayout(
                maxX = layout.int("max_x") ?: 0,
                maxY = layout.int("max_y") ?: 0,
                seats = seats,
            )
        }

    suspend fun reserveSeat(
        cookie: String,
        libId: Int,
        seatKey: String,
    ): Result<Unit> =
        request(
            cookie = cookie,
            operationName = "reserueSeat",
            query = MUTATION_RESERVE_SEAT,
            variables = mapOf(
                "libId" to libId,
                "seatKey" to seatKey,
                "captchaCode" to null,
                "captcha" to "",
            ),
        ).map { }

    private suspend fun request(
        cookie: String,
        operationName: String,
        query: String,
        variables: Map<String, Any?>,
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("operationName", operationName)
                .put("query", query)
                .put("variables", JSONObject(variables))
                .toString()
            val request = Request.Builder()
                .url(API_URL)
                .post(payload.toRequestBody(jsonMedia))
                .header("App-Version", APP_VERSION)
                .header("Accept", "application/json")
                .header("Origin", "https://web.traceint.com")
                .header("Referer", "https://web.traceint.com/")
                .apply {
                    if (cookie.isNotBlank()) header("Cookie", cookie)
                }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP " + response.code))
                }
                val body = response.body?.string().orEmpty()
                val root = JsonParser.parseString(body).asJsonObject
                val errors = root.getAsJsonArray("errors")
                if (errors != null && errors.size() > 0) {
                    val errorObj = errors[0].asJsonObject
                    val msg = errorObj.str("msg") ?: "请求失败"
                    val code = errorObj.int("code") ?: 0
                    return@withContext Result.failure(TraceIntException(code, msg))
                }
                val data = root.getAsJsonObject("data")
                    ?: return@withContext Result.failure(IllegalStateException("服务器返回空数据"))
                if (data.has("userAuth") && data.get("userAuth").isJsonNull) {
                    return@withContext Result.failure(IllegalStateException("登录已失效，请重新登录"))
                }
                Result.success(data)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun JsonObject?.obj(name: String): JsonObject? {
    val value = this?.get(name) ?: return null
    return if (value.isJsonObject) value.asJsonObject else null
}

private fun JsonObject?.str(name: String): String? {
    val value = this?.get(name) ?: return null
    return if (value.isJsonPrimitive) value.asString else null
}

private fun JsonObject?.int(name: String): Int? {
    val value = this?.get(name) ?: return null
    return if (value.isJsonPrimitive) runCatching { value.asInt }.getOrNull() else null
}
