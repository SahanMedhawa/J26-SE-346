// Run with: node --test tests/*.test.cjs
// Deterministic hook/effect tests of the actual components; no browser or live database.
const { test, before } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const React = require("react");

let componentCode, clientCode, pageCode;
before(async () => {
  const { transformWithOxc } = await import("vite");
  async function compile(relative) {
    const filename = path.join(__dirname, "..", relative);
    const source = fs.readFileSync(filename, "utf8").replace("function ProjectValidation(", "export function ProjectValidation(");
    return (await transformWithOxc(source, filename, { jsx: { runtime: "automatic" } })).code;
  }
  componentCode = await compile("src/modules/rules/RuleAssurance.tsx");
  clientCode = await compile("src/shared/api/client.ts");
  pageCode = await compile("src/modules/rules/RuleValidationPage.tsx");
});

function load(code, imports = {}) {
  const names = [];
  code = code.replace(/import\s*\{([^}]+)\}\s*from\s*(['"][^'"]+['"]);/g, (_, items, source) => `const {${items.replace(/\s+as\s+/g, ": ")}} = require(${source});`);
  code = code.replace(/export (async )?(function|const|class) (\w+)/g, (_, async, kind, name) => { names.push(name); return `${async || ""}${kind} ${name}`; });
  const module = { exports: {} };
  new Function("require", "module", "exports", `${code}\nmodule.exports = { ${names.join(", ")} };`)(name => name in imports ? imports[name] : require(name), module, module.exports);
  return module.exports;
}

const rule = { id: "rule-uuid", projectId: "healthmate-uuid", ruleId: "R-001", adrId: "adr-uuid", sourceElement: "PatientService", sourceElementType: "SERVICE", relationshipType: "CALLS", targetElement: "AppointmentService", targetElementType: "SERVICE", decision: "FORBIDDEN", severity: "HIGH", description: "A saved interpretation" };
const fingerprint = "a".repeat(64);
const workspace = { ruleUuid: rule.id, ruleId: rule.ruleId, adrUuid: rule.adrId, adrDisplayId: "ADR-001", adrVersion: 2, structuralValidationStatus: "SUPPORTED", structuralValidationCurrent: true, reviewStatus: "PENDING", confirmationEligible: true, blockedReasons: [], evidenceFingerprint: fingerprint, evidenceRevision: 3, sourceFileCount: 1, targetFileCount: 1, latestReview: null };
const stored = { ruleUuid: rule.id, status: "SUPPORTED", reason: "A supported structural shape", matchedSignatureId: "signature-1", architectureStyle: "MICROSERVICES", catalogueVersion: null, validatedFingerprint: "b".repeat(64), validatedAt: "2026-10-18T10:00:00Z", current: true, lastValidationStatus: "SUPPORTED", validatedRevision: 1, currentRevision: 1 };
const adr = { id: rule.adrId, adrId: "ADR-001", title: "Decision", version: 2, context: "Saved context", decision: "Saved decision", consequences: "Saved consequences", status: "ACCEPTED" };
function historyEntry(id, decision, decidedAt, current) { return { review: { id, ruleUuid: rule.id, decision, reviewer: "Architect", comments: "A historical explanation", decidedAt }, current, reviewerAuthenticated: false }; }
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }
function text(node) { if (node == null || typeof node === "boolean") return ""; if (typeof node !== "object") return String(node); return React.Children.toArray(node.props?.children).map(text).join(""); }
function find(node, predicate) {
  if (!node || typeof node !== "object") return undefined;
  if (predicate(node)) return node;
  for (const child of React.Children.toArray(node.props?.children)) { const match = find(child, predicate); if (match) return match; }
}

function mount(name = "ArchitectReviewPanel", overrides = {}, initial = {}) {
  const client = load(clientCode);
  const slots = [], pending = [], calls = [];
  let cursor = 0, changed = false, tree, evidenceRefreshes = 0, unmounted = false;
  const api = {
    list: async () => [rule],
    validateStructure: async () => ({ ruleUuid: rule.id, validationStatus: "SUPPORTED" }),
    storedStructuralValidation: async () => stored,
    adrFidelity: async () => ({ ruleUuid: rule.id, ruleId: rule.ruleId, adrUuid: rule.adrId, adrDisplayId: "ADR-001", status: "MATCH", explanation: "ADR decision text matches the linked rule." }),
    architectReview: async () => workspace,
    architectReviewHistory: async () => [],
    implementationMappings: async () => ({ ruleUuid: rule.id, sourceFiles: ["src/Patient.java"], targetFiles: ["src/Appointment.java"] }),
    get: async () => rule,
    submitArchitectReview: async () => ({ ...workspace, reviewStatus: "CONFIRMED" }),
    ...overrides,
  };
  const tracked = Object.fromEntries(Object.entries(api).map(([method, fn]) => [method, (...args) => { calls.push({ method, args }); return fn(...args); }]));
  const hooks = {
    useState(initialValue) {
      const index = cursor++;
      slots[index] ??= { value: typeof initialValue === "function" ? initialValue() : initialValue };
      return [slots[index].value, update => {
        assert.ok(!unmounted, "no state updates after unmount");
        const value = typeof update === "function" ? update(slots[index].value) : update;
        if (!Object.is(value, slots[index].value)) { slots[index].value = value; changed = true; }
      }];
    },
    useRef(value) { const index = cursor++; slots[index] ??= { value: { current: value } }; return slots[index].value; },
    useEffect(effect, deps) {
      const index = cursor++;
      if (!slots[index] || deps.some((value, i) => !Object.is(value, slots[index].deps[i]))) {
        const previous = slots[index]; slots[index] = { deps, cleanup: previous?.cleanup };
        pending.push(() => { previous?.cleanup?.(); slots[index].cleanup = effect(); });
      }
    },
  };
  const imports = { react: hooks, "../../shared/api/client": { ...client, rulesApi: tracked, adrsApi: { get: async (...args) => { calls.push({ method: "adr", args }); return adr; } } } };
  const children = { StoredValidationResult: () => null, AdrFidelityResult: () => null, ArchitectReviewPanel: () => null, ArchitectConfirmationRegister: () => null };
  const Component = name === "ProjectValidation" ? load(pageCode, { ...imports,
    "react-router-dom": { Link: () => null, useNavigate: () => destination => calls.push({ method: "navigate", args: [destination] }) },
    "./ArchitectureRulesPage": { DarkSelect: () => null }, "./rulesWorkspace": {}, "./RuleAssurance": children,
  })[name] : load(componentCode, imports)[name];
  let props = { projectId: rule.projectId, ruleUuid: rule.id, rule, rules: [rule], selectedRuleId: rule.id, onSelectRule: id => calls.push({ method: "selectRule", args: [id] }), project: { id: rule.projectId, architectureStyles: ["MICROSERVICES"] }, initialRuleId: rule.id, paused: false, refresh: 0, onEvidenceChanged: () => evidenceRefreshes++, onReviewSaved: () => calls.push({ method: "reviewSaved", args: [] }), ...initial };
  function render() {
    let passes = 0;
    do { assert.ok(passes++ < 20); cursor = 0; changed = false; tree = Component(props); while (pending.length) pending.shift()(); } while (changed);
  }
  render();
  return {
    calls, children, ApiClientError: client.ApiClientError,
    get text() { return text(tree); }, get tree() { return tree; }, get evidenceRefreshes() { return evidenceRefreshes; },
    button(label) { const result = find(tree, node => node.type === "button" && text(node) === label); assert.ok(result, label); return result; },
    click(label) { const button = this.button(label); assert.ok(!button.props.disabled, `${label} enabled`); button.props.onClick(); render(); },
    input(name, value) { const node = find(tree, item => item.props?.name === name); assert.ok(node); assert.ok(!node.props.disabled); node.props.onChange({ target: { value } }); render(); },
    update(next) { props = { ...props, ...next }; render(); },
    async flush() { for (let i = 0; i < 4; i++) { await new Promise(resolve => setImmediate(resolve)); render(); } },
    unmount() { for (const slot of slots) slot?.cleanup?.(); unmounted = true; },
  };
}

test("loads all three persisted statuses without treating load failures as NOT_VALIDATED", async () => {
  for (const status of ["NOT_VALIDATED", "SUPPORTED", "UNSUPPORTED"]) {
    const h = mount("StoredValidationResult", { storedStructuralValidation: async () => ({ ...stored, status, current: status !== "NOT_VALIDATED" }) });
    assert.match(h.text, /Loading persisted/); await h.flush(); assert.match(h.text, new RegExp(status));
    assert.match(h.text, /supported structural shape/); h.unmount();
  }
  const h = mount("StoredValidationResult", { storedStructuralValidation: async () => { throw new Error("Storage unavailable"); } });
  await h.flush(); assert.match(h.text, /Storage unavailable/); assert.ok(!h.text.includes("NOT_VALIDATED")); h.unmount();
});

test("shows ADR-rule fidelity status and explanation on the validation results panel", async () => {
  for (const status of ["MATCH", "MISMATCH", "REVIEW_REQUIRED"]) {
    const h = mount("AdrFidelityResult", { adrFidelity: async () => ({ ruleUuid: rule.id, ruleId: rule.ruleId, adrUuid: rule.adrId, adrDisplayId: "ADR-001", status, explanation: `Fidelity outcome ${status}` }) });
    assert.match(h.text, /Loading ADR/); await h.flush();
    assert.match(h.text, new RegExp(status));
    assert.match(h.text, new RegExp(`Fidelity outcome ${status}`));
    assert.match(h.text, /ADR-001/);
    assert.doesNotMatch(h.text, /PP1|must not directly access/);
    h.unmount();
  }
  const h = mount("AdrFidelityResult", { adrFidelity: async () => { throw new Error("Fidelity unavailable"); } });
  await h.flush(); assert.match(h.text, /Fidelity unavailable/); h.unmount();
});

test("stale structural evidence displays effective status and separately retains historical result", async () => {
  const h = mount("StoredValidationResult", { storedStructuralValidation: async () => ({ ...stored, status: "NOT_VALIDATED", current: false, lastValidationStatus: "SUPPORTED", reason: "Definition changed; rerun validation" }) });
  await h.flush(); assert.match(h.text, /NOT_VALIDATED/); assert.match(h.text, /SUPPORTED \(requires revalidation\)/); assert.match(h.text, /Definition changed/); assert.match(h.text, /Last validated/); h.unmount();
});

test("reloads persisted results after validation and rejects stale GET responses", async () => {
  const old = deferred(); let count = 0;
  const h = mount("StoredValidationResult", { storedStructuralValidation: async () => ++count === 1 ? old.promise : { ...stored, status: "UNSUPPORTED" } });
  h.update({ paused: true }); assert.match(h.text, /Refreshing/);
  h.update({ paused: false, refresh: 1 }); await h.flush(); old.resolve(stored); await h.flush();
  assert.match(h.text, /UNSUPPORTED/); assert.ok(!h.text.includes("Structural Validation: SUPPORTED")); h.unmount();
});

test("shows only implemented structural/review statuses and preserves ADR and mapping evidence", async () => {
  const h = mount(); await h.flush();
  assert.match(h.text, /PENDING/); assert.match(h.text, /SUPPORTED/);
  for (const unfinished of ["Full Formal Validation", "Conflict Detection", "Traceability/Currency", "NOT_EVALUATED", "READY"]) assert.ok(!h.text.includes(unfinished));
  assert.match(h.text, /Version 2/); assert.match(h.text, /Saved context/); assert.match(h.text, /src\/Patient.java/);
  assert.match(h.text, /FORBIDDEN/); assert.ok(!h.text.includes("READY"));
  assert.ok(h.button("Confirm Rule").props.disabled); h.unmount();
});

test("confirmation eligibility and blocked reasons come from backend, not a supported local shape", async () => {
  const h = mount("ArchitectReviewPanel", { architectReview: async () => ({ ...workspace, confirmationEligible: false, blockedReasons: ["At least one TARGET file is required."] }) });
  await h.flush(); h.input("reviewer", "Architect"); assert.match(h.text, /TARGET file is required/);
  assert.ok(h.button("Confirm Rule").props.disabled);
  h.input("reviewComments", "Please complete the missing target mapping."); assert.ok(!h.button("Request Changes").props.disabled); h.unmount();
});

test("confirmation sends exactly the displayed fingerprint and blocks duplicate submissions", async () => {
  const pending = deferred(); let confirmed = false;
  const h = mount("ArchitectReviewPanel", { submitArchitectReview: async () => pending.promise, architectReview: async () => ({ ...workspace, reviewStatus: confirmed ? "CONFIRMED" : "PENDING" }) });
  await h.flush(); h.input("reviewer", "  Software Architect  "); h.input("reviewComments", "Reviewed the saved interpretation.");
  const submit = h.button("Confirm Rule").props.onClick; submit(); submit(); await h.flush();
  const calls = h.calls.filter(call => call.method === "submitArchitectReview"); assert.equal(calls.length, 1);
  assert.deepEqual(calls[0].args, [rule.projectId, rule.id, { decision: "CONFIRMED", reviewer: "Software Architect", comments: "Reviewed the saved interpretation.", expectedEvidenceFingerprint: fingerprint }]);
  confirmed = true; pending.resolve({ ...workspace, reviewStatus: "CONFIRMED" }); await h.flush();
  assert.match(h.text, /Confirmation recorded/); assert.match(h.text, /CONFIRMED/);
  assert.equal(h.calls.filter(call => call.method === "reviewSaved").length, 1);
  assert.ok(h.calls.filter(call => call.method === "architectReviewHistory").length >= 2); h.unmount();
});

test("HTTP 409 refreshes evidence without retrying or replacing the submitted fingerprint", async () => {
  let reads = 0, h;
  h = mount("ArchitectReviewPanel", { architectReview: async () => ({ ...workspace, evidenceFingerprint: ++reads === 1 ? fingerprint : "c".repeat(64), reviewStatus: "PENDING" }), submitArchitectReview: async () => { throw new h.ApiClientError({ status: 409, message: "Reviewed evidence changed" }); } });
  await h.flush(); h.input("reviewer", "Architect"); h.input("reviewComments", "Keep these notes after conflict."); h.click("Confirm Rule"); await h.flush();
  assert.equal(h.calls.filter(call => call.method === "submitArchitectReview").length, 1);
  assert.equal(h.calls.find(call => call.method === "submitArchitectReview").args[2].expectedEvidenceFingerprint, fingerprint);
  assert.match(h.text, /Review the current evidence again/); assert.match(h.text, /c{64}/); assert.match(h.text, /PENDING/);
  assert.equal(find(h.tree, node => node.props?.name === "reviewComments").props.value, "Keep these notes after conflict.");
  assert.equal(h.evidenceRefreshes, 1); h.unmount();
});

test("change requests require meaningful comments and remain available for unsupported evidence", async () => {
  let decision = "PENDING";
  const h = mount("ArchitectReviewPanel", { architectReview: async () => ({ ...workspace, structuralValidationStatus: "UNSUPPORTED", confirmationEligible: false, reviewStatus: decision }), submitArchitectReview: async (_, __, input) => { decision = input.decision; return { ...workspace, reviewStatus: decision }; } });
  await h.flush(); h.input("reviewer", "Architect"); h.input("reviewComments", "short"); assert.ok(h.button("Request Changes").props.disabled);
  h.input("reviewComments", "Please revise this unsupported relationship."); h.click("Request Changes"); await h.flush();
  assert.equal(h.calls.find(call => call.method === "submitArchitectReview").args[2].decision, "CHANGES_REQUESTED"); assert.match(h.text, /CHANGES_REQUESTED/); assert.match(h.text, /Change request recorded/); h.unmount();
});

test("failed requests preserve reviewer notes and show actual backend validation error", async () => {
  let h;
  h = mount("ArchitectReviewPanel", { submitArchitectReview: async () => { throw new h.ApiClientError({ status: 400, message: "Validation failed", fieldErrors: { reviewer: "Reviewer rejected by backend" } }); } });
  await h.flush(); h.input("reviewer", "Architect"); h.input("reviewComments", "Notes remain available."); h.click("Confirm Rule"); await h.flush();
  assert.match(h.text, /Reviewer rejected by backend/); assert.ok(!h.text.includes("Confirmation recorded"));
  assert.equal(find(h.tree, node => node.props?.name === "reviewComments").props.value, "Notes remain available."); h.unmount();
});

test("history is newest-first and stale confirmations remain historical while effective status is pending", async () => {
  const old = historyEntry("old", "CONFIRMED", "2026-10-16T10:00:00Z", false);
  const recent = historyEntry("new", "CHANGES_REQUESTED", "2026-10-18T10:00:00Z", true);
  const h = mount("ArchitectReviewPanel", { architectReview: async () => ({ ...workspace, latestReview: old }), architectReviewHistory: async () => [old, recent] });
  await h.flush(); assert.match(h.text, /previous CONFIRMED decision applies to older evidence/); assert.match(h.text, /PENDING/);
  const body = find(h.tree, node => node.type === "tbody"); const rows = React.Children.toArray(body.props.children);
  assert.ok(text(rows[0]).startsWith("CHANGES_REQUESTED")); assert.ok(text(rows[1]).startsWith("CONFIRMED")); assert.match(text(rows[1]), /Historical \/ stale/);
  assert.ok(!find(body, node => node.type === "button" || node.type === "input")); h.unmount();
});

test("refresh clears confirmed indicators and trusts the newly effective backend status", async () => {
  let current = true;
  const h = mount("ArchitectReviewPanel", { architectReview: async () => ({ ...workspace, reviewStatus: current ? "CONFIRMED" : "PENDING" }) });
  await h.flush(); assert.match(h.text, /CONFIRMED/); current = false;
  h.update({ paused: true }); assert.ok(!h.text.includes("CONFIRMED"));
  h.update({ paused: false, refresh: 1 }); await h.flush(); assert.match(h.text, /PENDING/); assert.ok(!h.text.includes("CONFIRMED")); h.unmount();
});

test("workspace load failure leaves confirmation disabled and history visible", async () => {
  const old = historyEntry("old", "CONFIRMED", "2026-10-16T10:00:00Z", false);
  const h = mount("ArchitectReviewPanel", { architectReview: async () => { throw new Error("Review storage unavailable"); }, architectReviewHistory: async () => [old] });
  await h.flush(); assert.match(h.text, /Review storage unavailable/); assert.ok(h.button("Confirm Rule").props.disabled); assert.match(h.text, /Historical \/ stale/); h.unmount();
});

test("reading detailed evidence starts after the workspace fingerprint, and late replies cannot update an unmounted panel", async () => {
  const pending = deferred(); const h = mount("ArchitectReviewPanel", { architectReview: async () => pending.promise });
  assert.ok(!h.calls.some(call => call.method === "get" || call.method === "implementationMappings" || call.method === "adr"));
  h.unmount(); pending.resolve(workspace); await new Promise(resolve => setImmediate(resolve));
  assert.ok(!h.calls.some(call => call.method === "submitArchitectReview"));
});

test("API helpers use UUID routes, unchanged structural POST, exact review evidence and read-only history", async () => {
  const previousFetch = global.fetch; const calls = [];
  global.fetch = async (url, options) => { calls.push({ url, ...options }); return { ok: true, json: async () => workspace }; };
  try {
    const { rulesApi } = load(clientCode);
    await rulesApi.storedStructuralValidation(rule.projectId, rule.id); await rulesApi.adrFidelity(rule.projectId, rule.id); await rulesApi.architectReview(rule.projectId, rule.id); await rulesApi.architectReviewHistory(rule.projectId, rule.id);
    const input = { decision: "CONFIRMED", reviewer: "Architect", comments: "Reviewed", expectedEvidenceFingerprint: fingerprint };
    await rulesApi.submitArchitectReview(rule.projectId, rule.id, input); await rulesApi.validateStructure(rule.projectId, rule.id, { architectureStyle: "MICROSERVICES" });
    const base = `/api/rules/projects/${rule.projectId}/rules/${rule.id}`;
    assert.equal(calls[0].url, `${base}/structural-validation`); assert.equal(calls[1].url, `${base}/adr-fidelity`); assert.equal(calls[2].url, `${base}/architect-review`); assert.equal(calls[3].url, `${base}/architect-review/history`);
    assert.equal(calls[4].method, "POST"); assert.deepEqual(JSON.parse(calls[4].body), input); assert.equal(calls[5].url, `${base}/validate-structure`); assert.deepEqual(JSON.parse(calls[5].body), { architectureStyle: "MICROSERVICES" });
  } finally { global.fetch = previousFetch; }
});

test("existing validation POST refreshes both persisted panels and preserves UUID edit navigation", async () => {
  const previousWindow = global.window, previousDocument = global.document;
  const listeners = new Map();
  const events = { addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name) };
  global.window = events; global.document = { ...events, visibilityState: "visible" };
  try {
    const pending = deferred();
    const h = mount("ProjectValidation", { validateStructure: async () => pending.promise });
    await h.flush(); assert.ok(!h.button("Run Validation").props.disabled);
    const submit = h.button("Run Validation").props.onClick; submit(); submit(); await h.flush();
    assert.equal(h.calls.filter(call => call.method === "validateStructure").length, 1);
    assert.deepEqual(h.calls.find(call => call.method === "validateStructure").args, [rule.projectId, rule.id, { architectureStyle: "MICROSERVICES" }]);
    assert.equal(find(h.tree, node => node.type === h.children.StoredValidationResult).props.paused, true);
    assert.equal(find(h.tree, node => node.type === h.children.AdrFidelityResult).props.paused, true);
    listeners.get("focus")?.(); assert.equal(h.calls.filter(call => call.method === "list").length, 1);
    pending.resolve({ validationStatus: "SUPPORTED", ruleUuid: rule.id }); await h.flush();
    const persisted = find(h.tree, node => node.type === h.children.StoredValidationResult).props;
    const fidelity = find(h.tree, node => node.type === h.children.AdrFidelityResult).props;
    const review = find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props;
    assert.equal(persisted.ruleUuid, rule.id); assert.equal(persisted.paused, false); assert.equal(persisted.refresh, 1);
    assert.equal(fidelity.ruleUuid, rule.id); assert.equal(fidelity.paused, false); assert.equal(fidelity.refresh, 1);
    assert.equal(review.rule.id, rule.id); assert.equal(review.refresh, 1);
    let register = find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props;
    assert.equal(register.refresh, 1); assert.equal(register.rules[0].id, rule.id);
    review.onReviewSaved(); h.update({});
    register = find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props;
    assert.equal(register.refresh, 2);
    h.click("Edit Rule");
    const destination = h.calls.find(call => call.method === "navigate").args[0];
    assert.ok(destination.startsWith(`/projects/${rule.projectId}/rules/architecture-rules?`)); assert.ok(destination.includes(`editRule=${rule.id}`)); assert.ok(destination.includes("returnTo=validation"));
    listeners.get("visibilitychange")?.(); await h.flush(); assert.equal(h.calls.filter(call => call.method === "list").length, 1); h.unmount();
  } finally { global.window = previousWindow; global.document = previousDocument; }
});

test("validation derives each project's style and scopes validation, fidelity and review to that project", async () => {
  for (const [id, style] of [["healthmate-uuid", "MICROSERVICES"], ["petclinic-uuid", "LAYERED"]]) {
    const savedRule = { ...rule, id: `${id}-rule`, projectId: id };
    const h = mount("ProjectValidation", { list: async () => [savedRule], get: async () => savedRule }, {
      project: { id, architectureStyles: [style] }, initialRuleId: savedRule.id,
    });
    await h.flush(); h.click("Run Validation"); await h.flush();
    assert.deepEqual(h.calls.find(c => c.method === "list").args, [id]);
    assert.deepEqual(h.calls.find(c => c.method === "validateStructure").args, [id, savedRule.id, { architectureStyle: style }]);
    for (const child of [h.children.StoredValidationResult, h.children.AdrFidelityResult, h.children.ArchitectReviewPanel, h.children.ArchitectConfirmationRegister]) {
      assert.equal(find(h.tree, n => n.type === child).props.projectId, id);
    }
    h.unmount();
  }
});

test("window focus and visibility changes do not automatically reload validation rules", async () => {
  const previousWindow = global.window, previousDocument = global.document;
  const listeners = new Map();
  const events = { addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name) };
  global.window = events; global.document = { ...events, visibilityState: "visible" };
  let h;
  try {
    h = mount("ProjectValidation"); await h.flush();
    for (let index = 0; index < 3; index++) {
      listeners.get("focus")?.(); listeners.get("visibilitychange")?.(); await h.flush();
    }
    assert.equal(h.calls.filter(call => call.method === "list").length, 1);
    assert.equal(find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props.rule.id, rule.id);
    assert.equal(find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props.paused, false);
  } finally { h?.unmount(); global.window = previousWindow; global.document = previousDocument; }
});

test("explicit rules refresh preserves the selected rule on failure and clears it only after confirmed removal", async () => {
  let fail = false, present = true;
  const request = deferred();
  let delayed = true;
  const h = mount("ProjectValidation", { list: async () => {
    if (fail) { if (delayed) await request.promise; throw new Error("Rules temporarily unavailable"); }
    return present ? [rule] : [];
  } });
  await h.flush();
  const panel = () => find(h.tree, node => node.type === h.children.ArchitectReviewPanel);
  fail = true;
  panel().props.onEvidenceChanged(); h.update({});
  assert.equal(panel().props.paused, true);
  assert.equal(panel().props.rule.id, rule.id);
  assert.equal(h.calls.filter(call => call.method === "list").length, 2);
  request.resolve(); await h.flush();
  assert.match(h.text, /Rules temporarily unavailable/);
  assert.equal(panel().props.paused, false); assert.equal(panel().props.rule.id, rule.id);
  assert.equal(find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props.selectedRuleId, rule.id);
  await h.flush(); assert.equal(h.calls.filter(call => call.method === "list").length, 2);
  fail = false; delayed = false;
  h.click("Retry"); await h.flush(); assert.equal(panel().props.rule.id, rule.id);
  assert.ok(!h.text.includes("Rules temporarily unavailable"));
  present = false; panel().props.onEvidenceChanged(); h.update({}); await h.flush();
  assert.ok(!panel());
  const register = find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props;
  assert.equal(register.selectedRuleId, ""); assert.equal(register.paused, false); assert.deepEqual(register.rules, []); h.unmount();
});

test("ordinary project rerenders and rule selection do not restart project rules loading", async () => {
  const other = { ...rule, id: "second-rule", ruleId: "R-002" };
  const h = mount("ProjectValidation", { list: async () => [rule, other] }); await h.flush();
  for (let index = 0; index < 5; index++) {
    h.update({ project: { id: rule.projectId, architectureStyles: ["MICROSERVICES"] } }); await h.flush();
  }
  assert.equal(h.calls.filter(call => call.method === "list").length, 1);
  find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props.onSelectRule(other.id); h.update({}); await h.flush();
  assert.equal(find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props.rule.id, other.id);
  assert.equal(h.calls.filter(call => call.method === "list").length, 1);
  h.unmount();
});

test("evidence rerenders and changing callback identities do not refetch or leave loading active", async () => {
  const h = mount(); await h.flush();
  const reads = h.calls.length;
  for (let index = 0; index < 4; index++) {
    h.update({ rule: { ...rule }, onEvidenceChanged: () => {}, onReviewSaved: () => {} }); await h.flush();
  }
  h.input("reviewer", "Architect"); h.input("reviewComments", "Editing notes is not a refresh trigger."); await h.flush();
  assert.equal(h.calls.length, reads); assert.equal(h.tree.props["aria-busy"], false); h.unmount();
  const failed = mount("ArchitectReviewPanel", { architectReview: async () => { throw new Error("Unavailable"); } });
  await failed.flush(); assert.equal(failed.tree.props["aria-busy"], false);
  const failures = failed.calls.length; await failed.flush(); assert.equal(failed.calls.length, failures); failed.unmount();
});

test("a superseded rules response cannot overwrite a newer explicit refresh", async () => {
  const old = deferred(); let reads = 0;
  const latest = { ...rule, description: "Newest saved rule" };
  const h = mount("ProjectValidation", { list: async () => ++reads === 2 ? old.promise : [latest] }); await h.flush();
  const refresh = find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props.onEvidenceChanged;
  refresh(); h.update({}); refresh(); h.update({}); await h.flush();
  old.resolve([]); await h.flush();
  const panel = find(h.tree, node => node.type === h.children.ArchitectReviewPanel);
  assert.equal(panel.props.rule.description, "Newest saved rule"); assert.equal(panel.props.paused, false); h.unmount();
});

test("register shows effective statuses for real rule UUIDs and selects a rule without reloading statuses", async () => {
  const rules = [rule, { ...rule, id: "rule-b", ruleId: "R-002" }, { ...rule, id: "rule-c", ruleId: "R-003" }];
  const statuses = { [rule.id]: ["SUPPORTED", "CONFIRMED"], "rule-b": ["NOT_VALIDATED", "PENDING"], "rule-c": ["UNSUPPORTED", "CHANGES_REQUESTED"] };
  const h = mount("ArchitectConfirmationRegister", { architectReview: async (_, id) => ({ ...workspace, ruleUuid: id, structuralValidationStatus: statuses[id][0], reviewStatus: statuses[id][1], latestReview: historyEntry("old", "CONFIRMED", "2026-10-16T10:00:00Z", false) }) }, { rules });
  await h.flush();
  assert.match(h.text, /Architect Confirmation Register/);
  const rows = React.Children.toArray(find(h.tree, node => node.type === "tbody").props.children);
  for (let i = 0; i < rules.length; i++) {
    const cells = React.Children.toArray(rows[i].props.children);
    assert.equal(text(cells[2]), statuses[rules[i].id][0]); assert.equal(text(cells[3]), statuses[rules[i].id][1]);
    assert.match(text(cells[1]), /PatientService/);
  }
  assert.deepEqual(h.calls.filter(call => call.method === "architectReview").map(call => call.args), rules.map(item => [rule.projectId, item.id]));
  h.click("R-002"); assert.deepEqual(h.calls.find(call => call.method === "selectRule").args, ["rule-b"]);
  h.update({ selectedRuleId: "rule-b", rules: rules.map(item => ({ ...item })), onSelectRule: () => {} }); await h.flush();
  assert.equal(h.calls.filter(call => call.method === "architectReview").length, 3);
  assert.ok(h.button("R-002").props["aria-pressed"]); h.unmount();
});

test("register limits concurrency to four and completes without loading loops", async () => {
  const rules = Array.from({ length: 9 }, (_, index) => ({ ...rule, id: `uuid-${index}`, ruleId: `R-${index + 1}` }));
  const pending = new Map(rules.map(item => [item.id, deferred()]));
  let active = 0, peak = 0;
  const h = mount("ArchitectConfirmationRegister", { architectReview: async (_, id) => { active++; peak = Math.max(peak, active); await pending.get(id).promise; active--; return { ...workspace, ruleUuid: id }; } }, { rules });
  assert.equal(h.calls.length, 4); assert.match(h.text, /Loading current statuses/);
  await h.flush(); assert.equal(h.calls.length, 4);
  for (const request of pending.values()) request.resolve();
  await h.flush(); assert.equal(h.calls.length, 9); assert.equal(peak, 4); assert.ok(!h.text.includes("Loading..."));
  h.update({ rules: [...rules] }); await h.flush(); assert.equal(h.calls.length, 9); h.unmount();
});

test("register exposes failed rows as unavailable and retries without fabricating pending", async () => {
  let fail = true;
  const h = mount("ArchitectConfirmationRegister", { architectReview: async () => { if (fail) throw new Error("Review service unavailable"); return { ...workspace, reviewStatus: "CHANGES_REQUESTED" }; } });
  await h.flush(); assert.match(h.text, /Review service unavailable/);
  const body = find(h.tree, node => node.type === "tbody");
  assert.match(text(body), /Unavailable/); assert.ok(!text(body).includes("PENDING")); assert.ok(!text(body).includes("CONFIRMED"));
  fail = false; h.click("Retry Statuses"); await h.flush(); assert.match(h.text, /CHANGES_REQUESTED/); assert.ok(!h.text.includes("Unavailable")); h.unmount();
});

test("register drops obsolete project responses and refreshes after validation or review", async () => {
  const old = deferred();
  let status = "PENDING";
  const h = mount("ArchitectConfirmationRegister", { architectReview: async projectId => projectId === rule.projectId ? old.promise : { ...workspace, reviewStatus: status } });
  h.update({ projectId: "another-project" }); await h.flush(); assert.match(h.text, /PENDING/);
  old.resolve({ ...workspace, reviewStatus: "CONFIRMED" }); await h.flush(); assert.ok(!h.text.includes("CONFIRMED"));
  status = "CHANGES_REQUESTED"; h.update({ refresh: 1 }); await h.flush(); assert.match(h.text, /CHANGES_REQUESTED/);
  status = "CONFIRMED"; h.update({ paused: true }); assert.ok(!h.text.includes("CHANGES_REQUESTED"));
  h.update({ paused: false, refresh: 2 }); await h.flush(); assert.match(h.text, /CONFIRMED/); h.unmount();
});

test("register stops queued requests when unmounted and handles empty projects", async () => {
  const pending = deferred();
  const h = mount("ArchitectConfirmationRegister", { architectReview: async () => pending.promise }, { rules: Array.from({ length: 8 }, (_, index) => ({ ...rule, id: `uuid-${index}` })) });
  assert.equal(h.calls.length, 4); h.unmount(); pending.resolve(workspace); await new Promise(resolve => setImmediate(resolve)); assert.equal(h.calls.length, 4);
  const empty = mount("ArchitectConfirmationRegister", {}, { rules: [] }); await empty.flush(); assert.match(empty.text, /No saved rules/); assert.equal(empty.calls.length, 0); empty.unmount();
});

test("selecting a register rule changes the details shown above", async () => {
  const previousWindow = global.window, previousDocument = global.document;
  global.window = { addEventListener() {}, removeEventListener() {} }; global.document = { ...global.window, visibilityState: "visible" };
  try {
    const other = { ...rule, id: "another-rule-uuid", ruleId: "R-002" };
    const h = mount("ProjectValidation", { list: async () => [rule, other] }); await h.flush();
    find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props.onSelectRule(other.id); h.update({});
    assert.equal(find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props.rule.id, other.id);
    assert.equal(find(h.tree, node => node.type === h.children.StoredValidationResult).props.ruleUuid, other.id);
    assert.equal(find(h.tree, node => node.type === h.children.AdrFidelityResult).props.ruleUuid, other.id);
    let register = find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props;
    assert.equal(register.refresh, 0);
    assert.equal(find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props.refresh, 1);
    register.onSelectRule(other.id); h.update({});
    assert.equal(find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props.refresh, 2);
    register = find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props;
    register.onRefresh(); h.update({});
    assert.equal(find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props.paused, true);
    await h.flush();
    assert.equal(find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props.paused, false);
    assert.equal(find(h.tree, node => node.type === h.children.ArchitectReviewPanel).props.rule.id, other.id);
    assert.equal(h.calls.filter(call => call.method === "list").length, 2);
    assert.equal(find(h.tree, node => node.type === h.children.ArchitectConfirmationRegister).props.refresh, 0);
    h.unmount();
  } finally { global.window = previousWindow; global.document = previousDocument; }
});
