package routes.user

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import model.*
import org.koin.ktor.ext.inject
import routes.validation.ValidationService
import utils.Crypto
import utils.toHex

fun Route.userRouter() {
    val validationService: ValidationService by inject()
    val userService: UserService by inject()
    val crypto: Crypto by inject()

    suspend fun ApplicationCall.validateWithPhone(phone: String, nickname: String, hashedOtp: String) {
        val hashedPhone = Phone(crypto.hashContent(phone).toHex())

        // validate user with phone
        if (validationService.validateOtp(phone = hashedPhone, otp = hashedOtp)) {
            val user = User(nickname = nickname, phone = Phone(crypto.encrypt(phone)))
            when (val respond = userService.create(user)) {
                is UserResponse -> respond(HttpStatusCode.Created, respond.apply {
                    this.user.phone = Phone(phone)
                })
                is ErrorResponse -> respond(HttpStatusCode.BadRequest, respond)
            }
        }
    }

    suspend fun ApplicationCall.validateWithEmail(email: String, nickname: String, hashedOtp: String) {
        val hashedEmail = Email(crypto.hashContent(email).toHex())

        // validate user with email
        if (validationService.validateOtp(email = hashedEmail, otp = hashedOtp)) {
            val user = User(nickname = nickname, email = Email(crypto.encrypt(email)))
            when (val respond = userService.create(user)) {
                is UserResponse -> respond(HttpStatusCode.Created, respond.apply {
                    this.user.email = Email(email)
                })
                is ErrorResponse -> respond(HttpStatusCode.BadRequest, respond)
            }
        }
    }

    suspend fun ApplicationCall.createUser(phone: String? = null, email: String? = null, nickname: String, otp: String) {
        val hashedOtp = crypto.hashContent(otp).toHex()
        when {
            phone != null -> validateWithPhone(phone, nickname, hashedOtp)
            email != null -> validateWithEmail(email, nickname, hashedOtp)
            else -> respond(HttpStatusCode.BadRequest, ErrorResponse("verify code incorrect!!!"))
        }
    }

    route("/account") {
        post("/register") {
            with(call) {
                val contentType = request.contentType()
                if (contentType == ContentType.Application.FormUrlEncoded) {
                    val body = receiveParameters()
                    val nickname = body["nickname"]?.trim() ?: return@post respond(HttpStatusCode.BadRequest)
                    val otp = body["verificationCode"]?.trim() ?: return@post respond(HttpStatusCode.BadRequest)
                    val phone = body["phone"]?.trim()
                    val email = body["email"]?.trim()

                    // nickname and otp validation
                    if (nickname.isNotEmpty() && nickname.length <= 50 && otp.isNotEmpty() && otp.length == 6) {
                        // bad request when phone and email null or empty
                        if (phone.isNullOrEmpty() && email.isNullOrEmpty()) {
                            respond(HttpStatusCode.BadRequest, ErrorResponse("phone number or email address is empty or null"))
                        }

                        // phone validation
                        if (phone != null && Phone.isValid(phone)) {
                            createUser(phone = phone, nickname = nickname, otp = otp)
                        }
                        // email validation
                        else if (email != null && Email.isValid(email) && email.length <= 50) {
                            createUser(email = email, nickname = nickname, otp = otp)
                        } else {
                            respond(HttpStatusCode.BadRequest, ErrorResponse("phone number or email address isn't valid"))
                        }
                    } else respond(HttpStatusCode.BadRequest)
                } else respond(HttpStatusCode.UnsupportedMediaType)
            }
        }
    }
}