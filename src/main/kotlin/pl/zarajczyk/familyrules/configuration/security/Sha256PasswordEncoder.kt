package pl.zarajczyk.familyrules.configuration.security

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.sha256

@Service
class Sha256PasswordEncoder : PasswordEncoder {
    override fun encode(rawPassword: CharSequence?): String? {
        return rawPassword?.toString()?.sha256()
    }

    override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
        return encode(rawPassword) == encodedPassword
    }
}