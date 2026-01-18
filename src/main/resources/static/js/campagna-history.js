/**
 * Storico campagna (grafici da InfluxDB).
 * Flow:
 * 1) init() legge id campagna e tempi, configura grafici e slider.
 * 2) loadData() chiama /api/v1/campagne/{id}/dati con filtri e limit.
 * 3) renderData() distribuisce i punti nei dataset Chart.js per asse/sensore.
 * 4) lo slider "Tempo" ricarica i dati da un offset in secondi.
 */
window.BSNHistory = (() => {
    const palette = [
        '#e11d48', '#0ea5e9', '#10b981', '#f59e0b', '#8b5cf6', '#f97316',
        '#14b8a6', '#22c55e', '#ef4444', '#3b82f6', '#a855f7', '#84cc16'
    ];

    const axes = ['ax', 'ay', 'az', 'gx', 'gy', 'gz', 'mx', 'my', 'mz'];
    const charts = {};
    const datasets = {};
    let campaignId = null;
    let sensorList = [];
    let startTimestampMs = null;
    let useElapsedTime = false;
    let endTimestampMs = null;
    let sliderTimer = null;

    /**
     * Avvia lo storico: prepara dati base, grafici e bindings UI.
     */
    function init() {
        const root = document.getElementById('campaignHistoryRoot');
        if (!root) return;
        campaignId = Number(root.dataset.campaignId);
        if (root.dataset.startTime) {
            const parsed = Date.parse(root.dataset.startTime);
            if (!Number.isNaN(parsed)) {
                startTimestampMs = parsed;
                useElapsedTime = true;
            }
        }
        if (root.dataset.endTime) {
            const parsedEnd = Date.parse(root.dataset.endTime);
            if (!Number.isNaN(parsedEnd)) {
                endTimestampMs = parsedEnd;
            }
        }
        sensorList = Array.from(document.querySelectorAll('#historySensorList [data-sensor]'))
            .map(el => el.dataset.sensor.toUpperCase());
        applySensorColors();
        initCharts();
        bindControls();
        setupTimeSlider();
        loadData();
    }

    /**
     * Collega i controlli di refresh manuale.
     */
    function bindControls() {
        const refresh = document.getElementById('historyRefresh');
        if (refresh) {
            refresh.addEventListener('click', (event) => {
                event.preventDefault();
                loadData();
            });
        }
        const dbSelect = document.getElementById('historyDbSelect');
        if (dbSelect) {
            dbSelect.addEventListener('change', () => {
                loadData();
            });
        }
    }

    /**
     * Inizializza lo slider temporale in secondi dall'avvio campagna.
     * Se non ci sono tempi validi, il controllo viene disabilitato.
     */
    function setupTimeSlider() {
        const slider = document.getElementById('historyTimeRange');
        const label = document.getElementById('historyTimeLabel');
        if (!slider || !label) return;

        const maxSeconds = resolveDurationSeconds();
        slider.max = String(Math.max(0, Math.floor(maxSeconds)));
        slider.value = String(Math.min(Number(slider.value) || 0, Number(slider.max)));
        label.textContent = formatElapsed(slider.value);

        if (!useElapsedTime || Number(slider.max) === 0) {
            slider.disabled = true;
            return;
        }

        slider.addEventListener('input', () => {
            label.textContent = formatElapsed(slider.value);
            if (sliderTimer) {
                clearTimeout(sliderTimer);
            }
            sliderTimer = setTimeout(() => {
                loadData();
            }, 300);
        });
        slider.addEventListener('change', loadData);
    }

    /**
     * Calcola la durata in secondi per definire il massimo dello slider.
     */
    function resolveDurationSeconds() {
        if (startTimestampMs === null) return 0;
        if (endTimestampMs !== null) {
            return Math.max(0, Math.floor((endTimestampMs - startTimestampMs) / 1000));
        }
        return Math.max(0, Math.floor((Date.now() - startTimestampMs) / 1000));
    }

    /**
     * Applica colori coerenti tra lista sensori e dataset dei grafici.
     */
    function applySensorColors() {
        const items = document.querySelectorAll('#historySensorList [data-sensor]');
        items.forEach((item, index) => {
            const dot = item.querySelector('span');
            if (dot) {
                dot.style.background = palette[index % palette.length];
            }
        });
    }

    /**
     * Carica i dati da InfluxDB con filtri (sensori, limit, fromSeconds).
     */
    function loadData() {
        if (!campaignId) return;
        const sensorChecks = document.querySelectorAll('input[name="historySensors"]:checked');
        const limitInput = document.getElementById('historyLimit');
        const slider = document.getElementById('historyTimeRange');
        const params = new URLSearchParams();
        params.set('measurement', 'campaign');
        const dbSelect = document.getElementById('historyDbSelect');
        if (dbSelect && dbSelect.value && dbSelect.value !== '0') {
            params.set('dbId', dbSelect.value);
        }
        if (sensorChecks.length > 0) {
            const sensors = Array.from(sensorChecks)
                .map(input => input.value)
                .filter(Boolean);
            if (sensors.length > 0) {
                params.set('imuid', sensors.join(','));
            }
        }
        if (limitInput && limitInput.value) {
            params.set('limit', limitInput.value);
        }
        if (slider && !slider.disabled && slider.value) {
            params.set('fromSeconds', slider.value);
        }
        const url = `/api/v1/campagne/${campaignId}/dati?${params.toString()}`;
        setMessage('Caricamento dati...');

        fetch(url)
            .then(async res => {
                if (!res.ok) {
                    const data = await res.json().catch(() => null);
                    throw new Error(data && data.message ? data.message : 'Errore lettura dati');
                }
                return res.json();
            })
            .then(renderData)
            .catch(err => {
                clearCharts();
                setMessage(err.message || 'Errore lettura dati');
            });
    }

    /**
     * Popola i grafici a partire dalle colonne/righe Influx.
     */
    function renderData(data) {
        clearCharts();
        if (!data || !Array.isArray(data.values) || data.values.length === 0) {
            setMessage('Nessun dato disponibile per la campagna.');
            return;
        }
        setMessage('');

        const columns = data.columns || [];
        const idx = {};
        columns.forEach((col, i) => { idx[col] = i; });

        data.values.forEach(row => {
            const imuid = normalizeValue(row[idx.imuid]);
            const timeValue = normalizeValue(row[idx.time]);
            const timestamp = Date.parse(timeValue);
            if (!imuid || Number.isNaN(timestamp)) return;
            const xValue = useElapsedTime && startTimestampMs !== null
                ? Math.max(0, (timestamp - startTimestampMs) / 1000)
                : timestamp;

            axes.forEach(axis => {
                const colIndex = idx[axis];
                if (colIndex === undefined) return;
                const value = normalizeValue(row[colIndex]);
                if (value === null || value === undefined) return;
                const chartKey = `history-${axis}`;
                const dataset = ensureDataset(chartKey, imuid);
                dataset.data.push({ x: xValue, y: Number(value) });
            });
        });

        Object.values(charts).forEach(chart => {
            chart.data.datasets.forEach(ds => ds.data.sort((a, b) => a.x - b.x));
            chart.update('none');
        });
    }

    /**
     * Normalizza i valori Influx per evitare stringhe con spazi o null.
     */
    function normalizeValue(value) {
        if (value === null || value === undefined) return null;
        if (typeof value === 'string') return value.trim();
        return value;
    }

    /**
     * Svuota i dataset dei grafici mantenendo la configurazione.
     */
    function clearCharts() {
        Object.values(charts).forEach(chart => {
            chart.data.datasets = [];
            chart.update('none');
        });
        Object.keys(datasets).forEach(key => delete datasets[key]);
    }

    /**
     * Mostra un messaggio informativo (se il box e' presente).
     */
    function setMessage(text) {
        const msg = document.getElementById('historyMessage');
        if (msg) {
            msg.textContent = text;
        }
    }

    /**
     * Restituisce (o crea) il dataset per un sensore e un asse.
     */
    function ensureDataset(chartKey, sensor) {
        if (!datasets[chartKey]) datasets[chartKey] = {};
        if (!datasets[chartKey][sensor]) {
            const chart = charts[chartKey];
            const color = getColor(sensor);
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
        const index = sensorList.indexOf(sensor.toUpperCase());
        if (index === -1) {
            sensorList.push(sensor.toUpperCase());
            return palette[(sensorList.length - 1) % palette.length];
        }
        return palette[index % palette.length];
    }

    /**
     * Crea i grafici Chart.js per ciascun asse.
     */
    function initCharts() {
        axes.forEach(axis => createChart(`history-${axis}`));
    }

    /**
     * Inizializza un singolo grafico con stile compatto.
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
                                if (useElapsedTime) {
                                    return formatElapsed(value);
                                }
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
     * Formatta un tempo in secondi nel formato mm:ss o hh:mm:ss.
     */
    function formatElapsed(seconds) {
        const totalSeconds = Math.max(0, Math.floor(seconds));
        const hours = Math.floor(totalSeconds / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const secs = totalSeconds % 60;
        if (hours > 0) {
            return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
        }
        return `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    }

    return { init };
})();

document.addEventListener('DOMContentLoaded', () => {
    window.BSNHistory.init();
});
