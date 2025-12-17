import "./style.css";
import * as api from "./api";
import * as ws from "./ws";
import * as ui from "./ui";
import {resetForm} from "./ui";

const log = (msg) => document.getElementById("log").innerText = msg;

ui.highlight();

document.getElementById("startBtn").onclick = async () => {
    await api.startRecognition();
    ws.connect(handleRecognition);
    log("Распознавание запущено");
};

document.getElementById("stopBtn").onclick = async () => {
    await api.stopRecognition();
    ws.disconnect();
    log("Распознавание остановлено");
};

document.getElementById("saveBtn").onclick = async () => {
    const res = await api.saveReport(ui.getReportData());
    const json = await res.json();
    alert("Отчет сохранен. ID: " + json.reportId);
};

function handleRecognition(data) {
    console.log(data)

    if (!data.command) {
        ui.writeText(data.text);
        return;
    }

    switch (data.recognizedCommand) {

        case "NEXT_FIELD":
            ui.nextField();
            break;

        case "PREVIOUS_FIELD":
            ui.prevField();
            break;

        case "COMPLETE":
            api.saveReport(ui.getReportData());
            resetForm()
            break;

        default:
            if (data.recognizedCommand.startsWith("FIELD_")) {
                const fieldName =
                    data.recognizedCommand
                        .replace("FIELD_", "")
                        .replace("FIELD", "")
                        .toLowerCase();

                ui.setActiveFieldByName(fieldName);
            }
    }
}

