export const fields = [
    "patientFullName",
    "doctorFullName",
    "diagnosis",
    "operationDescription",
    "fillerFullName",
    "personalNumber"
];

export let activeFieldIndex = 0;

export function highlight() {
    fields.forEach((id, index) => {
        document.getElementById(id)
            .classList.toggle("active", index === activeFieldIndex);
    });
}

export function writeText(text) {
    const field = document.getElementById(fields[activeFieldIndex]);
    field.value += (field.value ? " " : "") + text;
}

export function nextField() {
    activeFieldIndex = Math.min(activeFieldIndex + 1, fields.length - 1);
    highlight();
}

export function prevField() {
    activeFieldIndex = Math.max(activeFieldIndex - 1, 0);
    highlight();
}

export function getReportData() {
    const report = {};
    fields.forEach(id => report[id] = document.getElementById(id).value);
    return report;
}

export function setActiveFieldByName(name) {
    const index = fields.findIndex(f =>
        f.toLowerCase().includes(name.toLowerCase())
    );
    if (index !== -1) {
        activeFieldIndex = index;
        highlight();
    }
}
