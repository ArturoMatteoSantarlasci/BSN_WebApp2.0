package it.polimi.bsnwebapp.Controller.web;

import it.polimi.bsnwebapp.DTO.request.SensoreCreateRequest;
import it.polimi.bsnwebapp.Model.Enum.Protocollo;
import it.polimi.bsnwebapp.Model.Enum.TipoMisura;
import it.polimi.bsnwebapp.Service.DatabaseConfigService;
import it.polimi.bsnwebapp.Service.ScriptCatalogService;
import it.polimi.bsnwebapp.Service.SensoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;

/**
 * Controller MVC per la pagina di amministrazione.
 * Prepara i dati per la view /admin (script disponibili e sensori) e gestisce upload script e creazione sensori.
 * Le operazioni sono riservate agli utenti con ruolo USER_DEV.
 */

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminPageController {

    private final ScriptCatalogService scriptCatalogService;
    private final SensoreService sensoreService;
    private final DatabaseConfigService databaseConfigService;

    @GetMapping
    public String adminRoot() {
        return "redirect:/admin/scripts";
    }

    @GetMapping("/scripts")
    public String admin(Model model) {
        model.addAttribute("scriptsAvailable", scriptCatalogService.listScripts());
        model.addAttribute("activeTab", "scripts");
        return "admin-scripts";
    }

    @GetMapping("/sensori")
    public String adminSensori(Model model) {
        model.addAttribute("sensori", sensoreService.listaSensori());
        model.addAttribute("activeTab", "sensori");
        return "admin-sensori";
    }

    @GetMapping("/database")
    public String adminDatabase(Model model) {
        model.addAttribute("databaseConfigs", databaseConfigService.listAll());
        model.addAttribute("activeTab", "database");
        return "admin-database";
    }

    @PostMapping("/scripts")
    public String uploadScript(@RequestParam("scriptFile") MultipartFile scriptFile,
                               RedirectAttributes redirectAttributes) {
        try {
            scriptCatalogService.uploadScript(scriptFile);
            redirectAttributes.addFlashAttribute("scriptUploadSuccess", "Script caricato con successo.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("scriptUploadError", e.getMessage());
        }
        return "redirect:/admin/scripts";
    }

    @PostMapping("/sensori")
    public String createSensore(@RequestParam String codice,
                                @RequestParam String nome,
                                @RequestParam String tipo,
                                @RequestParam Protocollo protocolloDefault,
                                @RequestParam(required = false) List<Protocollo> protocolliSupportati,
                                @RequestParam(required = false) List<TipoMisura> misureSupportate,
                                RedirectAttributes redirectAttributes) {
        try {
            SensoreCreateRequest request = new SensoreCreateRequest();
            request.setCodice(codice);
            request.setNome(codice);
            request.setTipo(tipo);
            request.setProtocolloDefault(protocolloDefault);
            request.setProtocolliSupportati(protocolliSupportati == null ? null : Set.copyOf(protocolliSupportati));
            request.setMisureSupportate(misureSupportate == null ? null : Set.copyOf(misureSupportate));
            sensoreService.creaSensore(request);
            redirectAttributes.addFlashAttribute("sensoreSuccess", "Sensore creato con successo.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("sensoreError", e.getMessage());
        }
        return "redirect:/admin/sensori";
    }

    @PostMapping("/sensori/{id}/elimina")
    public String deleteSensore(@PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        try {
            sensoreService.eliminaSensore(id);
            redirectAttributes.addFlashAttribute("sensoreSuccess", "Sensore eliminato con successo.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("sensoreError", e.getMessage());
        }
        return "redirect:/admin/sensori";
    }

    @PostMapping("/database")
    public String createDatabase(@RequestParam String nome,
                                 @RequestParam String host,
                                 @RequestParam String dbName,
                                 RedirectAttributes redirectAttributes) {
        try {
            databaseConfigService.create(nome, host, dbName);
            redirectAttributes.addFlashAttribute("databaseSuccess", "Database aggiunto con successo.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("databaseError", e.getMessage());
        }
        return "redirect:/admin/database";
    }

    @PostMapping("/database/{id}/elimina")
    public String deleteDatabase(@PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {
        try {
            databaseConfigService.delete(id);
            redirectAttributes.addFlashAttribute("databaseSuccess", "Database eliminato con successo.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("databaseError", e.getMessage());
        }
        return "redirect:/admin/database";
    }
}
