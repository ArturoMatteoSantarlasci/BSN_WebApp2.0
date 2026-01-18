/**
 * Live monitor della campagna.
 * Flusso: apre una connessione SSE verso /sse/imu, riceve eventi "imu" e "battery",
 * aggiorna i dataset Chart.js (uno per sensore e asse) e mostra lo stato sensori/batteria.
 */
window.BSNLiveMonitor = (() => {
    const palette = [
        '#e11d48', '#0ea5e9', '#10b981', '#f59e0b', '#8b5cf6', '#f97316',
        '#14b8a6', '#22c55e', '#ef4444', '#3b82f6', '#a855f7', '#84cc16'
    ];

    const charts = {};
    const datasets = {};
    const battery = {};
    const lastSeen = {};
    let eventSource = null;
    let campaignId = null;
    let campaignName = null;
    let sensorList = [];

    /**
     * Flow sintetico:
     * 1) {@link open()} inizializza UI e grafici
     * 2) {@link connectSse()} crea EventSource verso /sse/imu
     * 3) eventi imu/battery aggiornano dataset e stato sensori
     * 4) {@link stop()} termina la campagna, close() chiude la UI
     */

    /**
     * Avvia il monitor live per una campagna.
     * Config attesa: { id, nome, sensori }.
     */
    function open(config) {
        // Reset completo per evitare stato sporco tra campagne diverse.
        cleanup();
        campaignId = config.id;
        campaignName = config.nome || 'Campagna in corso';
        sensorList = (config.sensori || []).map(s => s.toUpperCase());
        resetUI();
        initCharts();
        renderSensors();
        connectSse();
        startStatusTimer();

        const modal = document.getElementById('liveMonitorModal');
        if (modal) {
            modal.showModal();
            if (!modal.dataset.boundClose) {
                modal.addEventListener('close', () => close(true));
                modal.dataset.boundClose = 'true';
            }
        }
    }

    /**
     * Termina la campagna tramite backend e chiude il monitor.
     */
    function stop() {
        // Termina la campagna lato backend e chiude il monitor.
        if (!campaignId) return;
        fetch(`/api/v1/campagne/${campaignId}/termina`, {
            method: 'POST',
            credentials: 'same-origin'
        })
            .then(async res => {
                if (!res.ok) {
                    const data = await res.json().catch(() => null);
                    throw new Error(data && data.message ? data.message : 'Errore terminazione');
                }
                if (typeof showSuccess === 'function') {
                    showSuccess('Campagna terminata con successo!');
                }
                if (window.htmx) {
                    htmx.ajax('GET', '/fragments/campagne/home', {
                        target: '#campagneTableBody',
                        swap: 'innerHTML'
                    });
                }
                close(false);
            })
            .catch(err => {
                alert(err.message || 'Errore terminazione');
            });
    }

    /**
     * Chiude il monitor senza terminare la campagna.
     * @param {boolean} fromModal true se la chiusura arriva dal dialog stesso.
     */
    function close(fromModal) {
        // Chiude il monitor e il modale, senza azioni sul backend.
        cleanup();
        if (!fromModal) {
            const modal = document.getElementById('liveMonitorModal');
            if (modal && modal.open) modal.close();
        }
    }

    /**
     * Ripulisce risorse e stato: SSE, grafici, cache sensori.
     */
    function cleanup() {
        // Chiude SSE, distrugge grafici e azzera gli stati locali.
        stopStatusTimer();
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }
        Object.values(charts).forEach(chart => chart.destroy());
        Object.keys(charts).forEach(key => delete charts[key]);
        Object.keys(datasets).forEach(key => delete datasets[key]);
        Object.keys(battery).forEach(key => delete battery[key]);
        Object.keys(lastSeen).forEach(key => delete lastSeen[key]);
        campaignId = null;
        campaignName = null;
        sensorList = [];
    }

    /**
     * Connette l'SSE e registra i listener per eventi imu/battery.
     */
    function connectSse() {
        // Apre il canale SSE con filtro opzionale per sensori.
        const params = new URLSearchParams();
        if (sensorList.length > 0) {
            params.set('imuid', sensorList.join(','));
        }
        const url = params.toString() ? `/sse/imu?${params}` : '/sse/imu';
        eventSource = new EventSource(url);

        eventSource.addEventListener('imu', (event) => {
            const payload = JSON.parse(event.data);
            handleImu(payload);
        });
        eventSource.addEventListener('battery', (event) => {
            const payload = JSON.parse(event.data);
            handleBattery(payload);
        });
        eventSource.onerror = () => {
            // ignored: reconnection is automatic
        };
    }

    /**
     * Aggiorna i grafici con i valori IMU ricevuti.
     */
    function handleImu(payload) {
        // Aggiorna grafici e stato sensore in base ai valori IMU.
        if (!payload || !payload.values) return;
        const sensor = (payload.imuid || '').toUpperCase();
        if (!sensor) return;
        if (!sensorList.includes(sensor)) {
            sensorList.push(sensor);
            renderSensors();
        }
        lastSeen[sensor] = Date.now();

        updateAxis(sensor, 'ax', payload.values.ax);
        updateAxis(sensor, 'ay', payload.values.ay);
        updateAxis(sensor, 'az', payload.values.az);
        updateAxis(sensor, 'gx', payload.values.gx);
        updateAxis(sensor, 'gy', payload.values.gy);
        updateAxis(sensor, 'gz', payload.values.gz);
        updateAxis(sensor, 'mx', payload.values.mx);
        updateAxis(sensor, 'my', payload.values.my);
        updateAxis(sensor, 'mz', payload.values.mz);
    }

    /**
     * Aggiorna la batteria e lo stato dei sensori.
     */
    function handleBattery(payload) {
        // Aggiorna solo lo stato batteria, senza grafici.
        if (!payload || !payload.values) return;
        const sensor = (payload.imuid || '').toUpperCase();
        if (!sensor) return;
        if (!sensorList.includes(sensor)) {
            sensorList.push(sensor);
        }
        battery[sensor] = payload.values.battery;
        lastSeen[sensor] = Date.now();
        renderSensors();
    }

    /**
     * Inserisce un punto nel grafico dell'asse specifico.
     */
    function updateAxis(sensor, axis, value) {
        // Inserisce il punto nel dataset corretto e mantiene una finestra scorrevole.
        if (value === null || value === undefined) return;
        const chartKey = `chart-${axis}`;
        const chart = charts[chartKey];
        if (!chart) return;
        const dataset = ensureDataset(chartKey, sensor);
        dataset.data.push({ x: Date.now(), y: Number(value) });
        if (dataset.data.length > 120) dataset.data.shift();
        chart.update('none');
    }

    /**
     * Restituisce (o crea) il dataset per un sensore/asse.
     */
    function ensureDataset(chartKey, sensor) {
        if (!datasets[chartKey]) datasets[chartKey] = {};
        if (!datasets[chartKey][sensor]) {
            const color = getColor(sensor);
            const chart = charts[chartKey];
            const dataset = {
                label: sensor,
                data: [],
                borderColor: color,
                backgroundColor: color,
                pointRadius: 0,
                pointHoverRadius: 0,
                pointHitRadius: 6,
                borderWidth: 1.5,
                tension: 0.25,
                fill: false
            };
            chart.data.datasets.push(dataset);
            datasets[chartKey][sensor] = dataset;
        }
        return datasets[chartKey][sensor];
    }

    /**
     * Assegna un colore stabile per sensore.
     */
    function getColor(sensor) {
        const index = sensorList.indexOf(sensor);
        if (index === -1) return palette[0];
        return palette[index % palette.length];
    }

    /**
     * Crea tutti i grafici (assiali) del live monitor.
     */
    function initCharts() {
        createChart('chart-ax');
        createChart('chart-ay');
        createChart('chart-az');
        createChart('chart-gx');
        createChart('chart-gy');
        createChart('chart-gz');
        createChart('chart-mx');
        createChart('chart-my');
        createChart('chart-mz');
    }

    /**
     * Inizializza un singolo grafico Chart.js con stile compatto.
     */
    function createChart(canvasId) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        const gridColor = 'rgba(255, 255, 255, 0.08)';
        const tickColor = 'rgba(255, 255, 255, 0.55)';
        charts[canvasId] = new Chart(ctx, {
            type: 'line',
            data: { datasets: [] },
            options: {
                animation: false,
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'nearest', intersect: false },
                elements: {
                    line: { borderWidth: 1.5 },
                    point: { radius: 0, hitRadius: 6, hoverRadius: 0 }
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'bottom',
                        labels: {
                            color: tickColor,
                            boxWidth: 10,
                            boxHeight: 10,
                            padding: 8,
                            usePointStyle: true,
                            pointStyle: 'line',
                            font: { size: 10 }
                        }
                    },
                    title: { display: false },
                    tooltip: { enabled: true }
                },
                scales: {
                    x: {
                        type: 'linear',
                        grid: { color: gridColor, drawBorder: false },
                        ticks: {
                            color: tickColor,
                            maxTicksLimit: 4,
                            autoSkip: true,
                            padding: 4,
                            callback: (value) => {
                                const d = new Date(value);
                                return d.toLocaleTimeString('it-IT', {
                                    hour: '2-digit',
                                    minute: '2-digit',
                                    second: '2-digit'
                                });
                            },
                            font: { size: 10 }
                        }
                    },
                    y: {
                        beginAtZero: false,
                        grid: { color: gridColor, drawBorder: false },
                        ticks: {
                            color: tickColor,
                            maxTicksLimit: 4,
                            padding: 4,
                            callback: (value) => Math.round(value),
                            font: { size: 10 }
                        }
                    }
                }
            }
        });
    }

    /**
     * Renderizza la lista sensori e i relativi stati/batteria.
     */
    function renderSensors() {
        const list = document.getElementById('liveSensorList');
        const batteryList = document.getElementById('liveBatteryList');
        if (!list || !batteryList) return;
        list.innerHTML = '';
        batteryList.innerHTML = '';

        sensorList.forEach(sensor => {
            const color = getColor(sensor);
            const seenAt = lastSeen[sensor];
            const isActive = seenAt && (Date.now() - seenAt) < 5000;

            const row = document.createElement('div');
            row.className = 'flex items-center gap-2';
            row.innerHTML = `
                <span class="inline-block w-2 h-2 rounded-full" style="background:${color}"></span>
                <span class="font-mono">${sensor}</span>
                <span class="text-xs ${isActive ? 'text-green-600' : 'text-gray-400'}">
                    ${isActive ? 'attivo' : 'inattivo'}
                </span>
            `;
            list.appendChild(row);

            const batteryRow = document.createElement('div');
            batteryRow.className = 'flex items-center justify-between gap-2 bg-base-100 rounded px-3 py-2';
            const value = battery[sensor] !== undefined ? battery[sensor] : 'N/A';
            batteryRow.innerHTML = `
                <span class="font-mono">${sensor}</span>
                <span class="font-semibold">${value}</span>
            `;
            batteryList.appendChild(batteryRow);
        });
    }

    /**
     * Aggiorna testo e stato iniziale del monitor.
     */
    function resetUI() {
        const name = document.getElementById('liveCampaignName');
        const meta = document.getElementById('liveCampaignMeta');
        if (name) name.textContent = campaignName;
        if (meta) meta.textContent = 'Monitoraggio in tempo reale';
        renderSensors();
    }

    let statusTimer = null;
    /**
     * Avvia il timer per aggiornare lo stato attivo/inattivo dei sensori.
     */
    function startStatusTimer() {
        stopStatusTimer();
        statusTimer = setInterval(() => renderSensors(), 1000);
    }

    /**
     * Ferma il timer di aggiornamento stato sensori.
     */
    function stopStatusTimer() {
        if (statusTimer) {
            clearInterval(statusTimer);
            statusTimer = null;
        }
    }

    /**
     * Apre il monitor live a partire dai data-attribute del bottone.
     */
    function openFromButton(button) {
        if (!button) return;
        const id = Number(button.dataset.id);
        const nome = button.dataset.nome || '';
        const sensori = button.dataset.sensors ? button.dataset.sensors.split(',') : [];
        open({ id, nome, sensori });
    }

    /**
     * Apre il monitor live partendo dalla risposta JSON di avvio campagna.
     */
    function openFromResponse(xhr) {
        if (!xhr || !xhr.responseText) return;
        try {
            const data = JSON.parse(xhr.responseText);
            if (!data || !data.id) return;
            const sensori = Array.isArray(data.sensori)
                ? data.sensori.map(s => s.codice)
                : [];
            open({ id: data.id, nome: data.nome, sensori });
        } catch (_) {
            // ignore
        }
    }

    return { open, openFromButton, openFromResponse, stop, close };
})();
