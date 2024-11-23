package pl.zarajczyk.familyrules.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.shared.DbConnector
import pl.zarajczyk.familyrules.shared.UserDto

@Service
class UsersService(private val dbConnector: DbConnector) : UserDetailsManager {
    override fun createUser(user: UserDetails?) {
        TODO("Not yet implemented")
    }

    override fun updateUser(user: UserDetails?) {
        TODO("Not yet implemented")
    }

    override fun deleteUser(username: String?) {
        TODO("Not yet implemented")
    }

    override fun changePassword(oldPassword: String?, newPassword: String?) {
        TODO("Not yet implemented")
    }

    override fun userExists(username: String?): Boolean {
        TODO("Not yet implemented")
    }

    override fun loadUserByUsername(username: String): UserDetails {
        return dbConnector.findUser(username)
            ?.let { dto -> DtoBasedUserDetails(dto) }
            ?: throw UsernameNotFoundException("User ≪$username≫ not found")
    }
}

data class DtoBasedUserDetails(private val dto: UserDto) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()

    override fun getPassword(): String = dto.passwordSha256

    override fun getUsername(): String = dto.username

}