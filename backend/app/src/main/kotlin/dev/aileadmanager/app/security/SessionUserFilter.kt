package dev.aileadmanager.app.security

import dev.aileadmanager.core.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class SessionUserFilter(private val users: UserRepository) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val userId = request.getSession(false)?.getAttribute(USER_ID_ATTRIBUTE) as? Long
        val user = userId?.let(users::findById)
        val id = user?.id
        if (id != null) {
            val principal = CurrentUser(id, user.telegramUserId, user.displayName, user.role)
            val authentication = UsernamePasswordAuthenticationToken(
                principal, null, listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
            )
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val USER_ID_ATTRIBUTE = "authenticatedUserId"
    }
}
