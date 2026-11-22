package com.example.demo.config;

import com.example.demo.service.CompteUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Tant qu'un utilisateur porte un mot de passe temporaire (donné par son capitaine),
 * toute page autre que le changement de mot de passe le redirige vers cette page.
 * Ce filtre est créé dans SecurityConfig : ce n'est pas un bean (sinon il serait exécuté deux fois).
 */
public class ChangementMotDePasseFilter extends OncePerRequestFilter {

    static final String PAGE = "/mot-de-passe";
    private static final List<String> CHEMINS_LIBRES =
            List.of(PAGE, "/deconnexion", "/connexion", "/error");
    private static final List<String> PREFIXES_LIBRES = List.of("/css/", "/js/", "/images/");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CompteUserDetails u
                && u.isMotDePasseTemporaire()
                && !cheminLibre(chemin(request))) {
            response.sendRedirect(request.getContextPath() + PAGE);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean cheminLibre(String chemin) {
        return CHEMINS_LIBRES.contains(chemin) || PREFIXES_LIBRES.stream().anyMatch(chemin::startsWith);
    }

    private static String chemin(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexte = request.getContextPath();
        return contexte == null || contexte.isEmpty() ? uri : uri.substring(contexte.length());
    }
}