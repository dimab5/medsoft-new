const API_BASE = "/api/test/voice";

export async function startRecognition() {
    return fetch(`${API_BASE}/start-continuous`, {
        method: "POST"
    });
}

export async function stopRecognition() {
    return fetch(`${API_BASE}/stop-continuous`, {
        method: "POST"
    });
}

export async function saveReport(report) {
    return fetch(`${API_BASE}/save-report`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(report)
    });
}
