/**
 * Frontend campagne: gestione form, modali e interazioni HTMX.
 * Flow principale:
 * 1) selezione tipo campagna -> apre modale configurazione e imposta lo script.
 * 2) submit campagna/paziente -> HTMX, con refresh frammenti senza reload pagina.
 * 3) azioni campagna (termina/elimina) -> refresh tabella home.
 * 4) ricerca paziente -> suggerimenti sotto input con selezione rapida.
 */

// Inizializzazione base
window.addEventListener('DOMContentLoaded', () => {
    wireTipoConfig();
    wireTipoConfigButtons();
    wireFrequenzaSlider();
    wirePersonaSearch();
});

/**
 * Collega la selezione dei tipi campagna ai radio button.
 */
function wireTipoConfig() {
    const radios = document.querySelectorAll('.tipo-radio');
    radios.forEach(radio => {
        radio.addEventListener('change', () => applyTipoSelection(radio));
        if (radio.checked) applyTipoSelection(radio);
    });
}

/**
 * Applica la selezione del tipo campagna:
 * imposta lo script e apre il modale di configurazione.
 */
function applyTipoSelection(radio) {
    const scriptName = radio.dataset.script || '';
    selectScriptByName(scriptName);

    openConfigModal();
}

/**
 * Gestisce il click sul bottone "Configura" vicino a ogni tipo.
 */
function wireTipoConfigButtons() {
    if (document.body.dataset.tipoConfigBound) return;
    document.body.dataset.tipoConfigBound = 'true';
    document.body.addEventListener('click', (event) => {
        const button = event.target.closest('[data-action="config-tipo"]');
        if (!button) return;
        const typeId = button.dataset.typeId;
        const radio = typeId ? document.getElementById(`tipo_${typeId}`) : null;
        if (radio) {
            radio.checked = true;
            applyTipoSelection(radio);
        } else {
            openConfigModal();
        }
    });
}

/**
 * Ripristina i campi del modale di configurazione campagna.
 */
function resetCampagnaConfigUI() {
    const scriptInput = document.getElementById('scriptFileName');
    if (scriptInput) scriptInput.value = '';
    const frequenza = document.getElementById('frequenzaCampagna');
    if (frequenza) frequenza.value = '50';
    syncFrequenzaLabel();
    const connettivita = document.getElementById('connettivitaCampagna');
    if (connettivita) connettivita.value = '';
    const dbConfig = document.getElementById('dbConfigId');
    if (dbConfig) dbConfig.value = '0';
    document.querySelectorAll('input[name="idSensori"]').forEach(cb => cb.checked = false);
}

/**
 * Apre il modale di configurazione solo se un tipo e' selezionato.
 */
function openConfigModal() {
    const selected = document.querySelector('.tipo-radio:checked');
    if (!selected) {
        showCampagnaError('Seleziona un tipo di campagna prima di configurare.');
        return;
    }
    const configModal = document.getElementById('configCampagnaModal');
    if (configModal) configModal.showModal();
}

/**
 * Collega lo slider della frequenza al label di supporto.
 */
function wireFrequenzaSlider() {
    const slider = document.getElementById('frequenzaCampagna');
    if (!slider) return;
    slider.addEventListener('input', syncFrequenzaLabel);
    syncFrequenzaLabel();
}

/**
 * Aggiorna il label della frequenza in Hz.
 */
function syncFrequenzaLabel() {
    const slider = document.getElementById('frequenzaCampagna');
    const label = document.getElementById('frequenzaValue');
    if (!slider || !label) return;
    label.textContent = `${slider.value} Hz`;
}

/**
 * Seleziona lo script dal select in base al nome suggerito dal tipo.
 */
function selectScriptByName(scriptName) {
    const scriptSelect = document.getElementById('scriptFileName');
    if (!scriptSelect) return;
    if (!scriptName) {
        scriptSelect.value = '';
        return;
    }
    const option = Array.from(scriptSelect.options).find(opt => opt.value === scriptName);
    if (option) {
        scriptSelect.value = scriptName;
    } else {
        scriptSelect.value = '';
        showCampagnaError(`Script non trovato: ${scriptName}`);
    }
}
// Gestione HTMX

document.body.addEventListener('htmx:afterRequest', (event) => {
    const targetId = event.target.id;

    if (targetId === 'createPersonaForm') {
        handlePersonaResponse(event);
    }

    if (targetId === 'createCampagnaForm') {
        handleCampagnaResponse(event);
    }

    if (event.target.matches('button[hx-post*="/api/v1/campagne/"]')) {
        handleCampagnaAction(event);
    }
});

document.body.addEventListener('htmx:afterSwap', (event) => {
    if (event.target && event.target.id === 'pazienteSelectWrapper') {
        wirePersonaSearch();
    }
});

function handlePersonaResponse(event) {
    const { successful, xhr } = event.detail;
    const createdPersonaId = extractIdFromResponse(xhr);

    if (successful) {
        hidePersonaError();
        document.getElementById('createPersonaForm').reset();
        document.getElementById('createPersonaModal').close();
        showSuccess('Paziente creato con successo!');

        // aggiorna select pazienti
        htmx.ajax('GET', '/fragments/persone/select', {
            target: '#pazienteSelectWrapper',
            swap: 'outerHTML'
        });
        if (createdPersonaId) {
            setTimeout(() => {
                const select = document.getElementById('selectPaziente');
                if (select) select.value = String(createdPersonaId);
            }, 150);
        }
        return;
    }

    const message = parseErrorMessage(xhr);
    showPersonaError(message);
}

/**
 * Gestisce l'esito della creazione campagna (successo/errore).
 */
function handleCampagnaResponse(event) {
    const { successful, xhr } = event.detail;

    if (successful) {
        hideCampagnaError();
        document.getElementById('createCampagnaForm').reset();
        resetCampagnaConfigUI();
        document.getElementById('createCampagnaModal').close();
        const configModal = document.getElementById('configCampagnaModal');
        if (configModal) configModal.close();
        showSuccess('Campagna avviata con successo!');
        if (window.BSNLiveMonitor) {
            window.BSNLiveMonitor.openFromResponse(xhr);
        }

        // aggiorna tabella home
        htmx.ajax('GET', '/fragments/campagne/home', {
            target: '#campagneTableBody',
            swap: 'innerHTML'
        });
        return;
    }

    const message = parseErrorMessage(xhr);
    showCampagnaError(message);
}

/**
 * Gestisce le azioni sulle campagne (termina/elimina).
 */
function handleCampagnaAction(event) {
    const { successful, xhr } = event.detail;
    if (!successful) {
        showCampagnaError(parseErrorMessage(xhr));
        return;
    }
    const action = event.target.getAttribute('hx-post') || '';
    if (action.includes('/termina')) {
        showSuccess('Campagna terminata con successo!');
        if (window.BSNLiveMonitor) {
            window.BSNLiveMonitor.close();
        }
    } else {
        showSuccess('Operazione completata con successo!');
    }
    htmx.ajax('GET', '/fragments/campagne/home', {
        target: '#campagneTableBody',
        swap: 'innerHTML'
    });
}

document.body.addEventListener('click', (event) => {
    const button = event.target.closest('[data-action="live"]');
    if (!button || !window.BSNLiveMonitor) return;
    window.BSNLiveMonitor.openFromButton(button);
});

// Modali

/**
 * Apre il modale di avvio nuova campagna.
 */
function openCreateCampagnaModal() {
    document.getElementById('createCampagnaModal').showModal();
}

/**
 * Apre il modale di creazione paziente e ripristina quello campagna al termine.
 */
function openCreatePersonaModal() {
    const campagnaModal = document.getElementById('createCampagnaModal');
    const personaModal = document.getElementById('createPersonaModal');
    if (!personaModal) return;
    const shouldReturn = campagnaModal && campagnaModal.open;
    personaModal.dataset.returnToCampagna = shouldReturn ? 'true' : 'false';
    if (campagnaModal && campagnaModal.open) {
        campagnaModal.close();
    }
    if (!personaModal.dataset.boundClose) {
        personaModal.addEventListener('close', () => restoreCampagnaModal());
        personaModal.dataset.boundClose = 'true';
    }
    personaModal.showModal();
}

/**
 * Riapre il modale campagna se era aperto prima della creazione paziente.
 */
function restoreCampagnaModal() {
    const personaModal = document.getElementById('createPersonaModal');
    if (!personaModal || personaModal.dataset.returnToCampagna !== 'true') return;
    const campagnaModal = document.getElementById('createCampagnaModal');
    if (campagnaModal && !campagnaModal.open) {
        campagnaModal.showModal();
    }
}

/**
 * Ricerca paziente: mostra suggerimenti e seleziona il valore nella select.
 */
function wirePersonaSearch() {
    const input = document.getElementById('searchPaziente');
    const select = document.getElementById('selectPaziente');
    const results = document.getElementById('pazienteSearchResults');
    if (!input || !select || !results) return;
    if (input.dataset.bound) return;
    input.dataset.bound = 'true';

    const clearResults = () => {
        results.innerHTML = '';
        results.classList.add('hidden');
    };

    const buildResults = () => {
        const query = (input.value || '').trim().toLowerCase();
        results.innerHTML = '';
        if (!query) {
            clearResults();
            return;
        }
        const matches = Array.from(select.options)
            .slice(1)
            .filter(option => option.textContent.toLowerCase().includes(query));
        if (!matches.length) {
            clearResults();
            return;
        }
        matches.forEach(option => {
            const item = document.createElement('button');
            item.type = 'button';
            item.dataset.value = option.value;
            item.dataset.label = option.textContent;
            item.className = 'w-full text-left px-2 py-1 text-sm hover:bg-base-200';
            item.textContent = option.textContent;
            results.appendChild(item);
        });
        results.classList.remove('hidden');
    };

    input.addEventListener('input', buildResults);
    input.addEventListener('focus', buildResults);
    input.addEventListener('blur', () => clearResults());
    input.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            clearResults();
        }
    });

    results.addEventListener('mousedown', (event) => {
        if (event.target.closest('[data-value]')) {
            event.preventDefault();
        }
    });

    results.addEventListener('click', (event) => {
        const item = event.target.closest('[data-value]');
        if (!item) return;
        select.value = item.dataset.value;
        input.value = item.dataset.label;
        clearResults();
    });

    select.addEventListener('change', () => {
        const option = select.options[select.selectedIndex];
        input.value = option && option.value ? option.textContent : '';
    });
}

// Messaggi

function showSuccess(message) {
    const alert = document.getElementById('successAlert');
    const text = document.getElementById('successAlertText');
    if (!alert || !text) return;
    text.textContent = message;
    alert.classList.remove('hidden');
    setTimeout(() => alert.classList.add('hidden'), 3000);
}

/**
 * Mostra l'errore nel box della creazione campagna.
 */
function showCampagnaError(message) {
    const box = document.getElementById('createCampagnaError');
    const text = document.getElementById('createCampagnaErrorText');
    if (!box || !text) return;
    text.textContent = message;
    box.classList.remove('hidden');
}

/**
 * Nasconde il box errori della creazione campagna.
 */
function hideCampagnaError() {
    const box = document.getElementById('createCampagnaError');
    if (box) box.classList.add('hidden');
}

/**
 * Mostra l'errore nel box della creazione paziente.
 */
function showPersonaError(message) {
    const box = document.getElementById('createPersonaError');
    const text = document.getElementById('createPersonaErrorText');
    if (!box || !text) return;
    text.textContent = message;
    box.classList.remove('hidden');
}

/**
 * Nasconde il box errori della creazione paziente.
 */
function hidePersonaError() {
    const box = document.getElementById('createPersonaError');
    if (box) box.classList.add('hidden');
}

/**
 * Estrae un messaggio leggibile dalla risposta HTTP.
 */
function parseErrorMessage(xhr) {
    if (!xhr) return 'Errore sconosciuto';
    const raw = xhr.responseText || xhr.statusText || 'Errore';
    try {
        const parsed = JSON.parse(raw);
        if (parsed && parsed.message) return parsed.message;
    } catch (_) {
        // ignore JSON parse errors
    }
    return raw;
}

/**
 * Estrae l'id creato dal body JSON (se presente).
 */
function extractIdFromResponse(xhr) {
    if (!xhr || !xhr.responseText) return null;
    try {
        const parsed = JSON.parse(xhr.responseText);
        return parsed && parsed.id ? parsed.id : null;
    } catch (_) {
        return null;
    }
}
