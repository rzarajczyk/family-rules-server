package pl.zarajczyk.familyrules.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.AccessLevel
import pl.zarajczyk.familyrules.domain.UserDto
import pl.zarajczyk.familyrules.domain.UsersRepository

@Service
class SpringSecurityUserDetailsManager(private val usersRepository: UsersRepository) : UserDetailsManager {
    override fun createUser(user: UserDetails?) {
        // SpringSecurity is used only for the authorization, not to user management
        TODO("Not implemented")
    }

    override fun updateUser(user: UserDetails?) {
        // SpringSecurity is used only for the authorization, not to user management
        TODO("Not implemented")
    }

    override fun deleteUser(username: String?) {
        // SpringSecurity is used only for the authorization, not to user management
        TODO("Not implemented")
    }

    override fun changePassword(oldPassword: String?, newPassword: String?) {
        // SpringSecurity is used only for the authorization, not to user management
        TODO("Not implemented")
    }

    override fun userExists(username: String?): Boolean {
        // SpringSecurity is used only for the authorization, not to user management
        TODO("Not implemented")
    }

    override fun loadUserByUsername(username: String): UserDetails {
        return usersRepository.get(username)
            ?.let { usersRepository.fetchDetails(it) }
            ?.let { dto -> DtoBasedUserDetails(dto) }
            ?: throw UsernameNotFoundException("User ≪$username≫ not found")
    }
}

data class DtoBasedUserDetails(private val dto: UserDto) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(
        AccessLevelAuthority(dto.accessLevel)
    )

    override fun getPassword(): String = dto.passwordSha256

    override fun getUsername(): String = dto.username

}

class AccessLevelAuthority(private val accessLevel: AccessLevel) : GrantedAuthority {
    override fun getAuthority(): String = "ROLE_${accessLevel.name}"
}