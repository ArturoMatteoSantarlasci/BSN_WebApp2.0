package it.polimi.bsnwebapp.Controller.web;

import it.polimi.bsnwebapp.Model.Entities.Campagna;
import it.polimi.bsnwebapp.Model.Enum.StatoCampagna;
import it.polimi.bsnwebapp.Repository.CampagnaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomePageController {

    private final CampagnaRepository campagnaRepository;

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {

        List<Campagna> campagneHome = campagnaRepository.findHomeCampaigns(
                List.of(StatoCampagna.PIANIFICATA, StatoCampagna.IN_CORSO)
        );
        model.addAttribute("campagneHome", campagneHome);

        boolean isDev = authentication.getAuthorities().stream()
                .anyMatch(a -> "USER_DEV".equals(a.getAuthority()));
        model.addAttribute("isDev", isDev);

        return "index";
    }
}
