// Run with: node --test tests/implementation-mappings.test.cjs
// These are mocked component/API tests, not browser or live backend tests.
const { test, before } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const React = require("react");

let componentCode, clientCode;
before(async () => {
  const { transformWithOxc } = await import("vite");
  async function compile(relative) {
    const filename = path.join(__dirname, "..", relative);
    return (await transformWithOxc(fs.readFileSync(filename, "utf8"), filename, { jsx: { runtime: "automatic" } })).code;
  }
  componentCode = await compile("src/modules/rules/ImplementationMappingsModal.tsx");
  clientCode = await compile("src/shared/api/client.ts");
});

// Exercise the actual component and effect cleanup without adding DOM/test packages.
function load(code, imports = {}) {
  const names = [];
  code = code.replace(/import\s*\{([^}]+)\}\s*from\s*(['"][^'"]+['"]);/g, (_, items, source) => `const {${items.replace(/\s+as\s+/g, ": ")}} = require(${source});`);
  code = code.replace(/export (async )?(function|const|class) (\w+)/g, (_, async, kind, name) => { names.push(name); return `${async || ""}${kind} ${name}`; });
  const module = { exports: {} };
  new Function("require", "module", "exports", `${code}\nmodule.exports = { ${names.join(", ")} };`)(name => {
    if (name in imports) return imports[name];
    return require(name);
  }, module, module.exports);
  return module.exports;
}

const projects = [
  { id: "project-a", name: "Project A", repositoryUrl: "https://github.com/owner/repo-a" },
  { id: "project-b", name: "Project B", repositoryUrl: "https://github.com/owner/repo-b" },
];
function makeRule(id, projectId = "project-a") {
  return { id, projectId, ruleId: "R-007", adrId: "adr-uuid", adrDisplayId: "ADR-032", sourceElement: "OrderService", sourceElementType: "SERVICE", relationshipType: "CALLS", targetElement: "PaymentService", targetElementType: "SERVICE", decision: "ALLOWED", severity: "HIGH" };
}
const ruleA = makeRule("rule-uuid-a");
const ruleB = makeRule("rule-uuid-b", "project-b");
const empty = { sourceFiles: [], targetFiles: [] };
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function text(node) {
  if (node == null || typeof node === "boolean") return "";
  if (typeof node !== "object") return String(node);
  return React.Children.toArray(node.props?.children).map(text).join("");
}
function find(node, predicate) {
  if (!node || typeof node !== "object") return undefined;
  if (predicate(node)) return node;
  for (const child of React.Children.toArray(node.props?.children)) {
    const match = find(child, predicate);
    if (match) return match;
  }
}

function mount(overrides = {}, projectData = projects) {
  const slots = [], pending = [], calls = [];
  let cursor = 0, changed = false, tree, closed = 0, confirmResult = true, confirmations = 0;
  const api = {
    list: async id => id === "project-a" ? [ruleA] : [ruleB],
    implementationMappings: async (projectId, ruleUuid) => ({ ruleUuid, ...empty }),
    repositoryFiles: async projectId => ({ projectId, repositoryUrl: "https://github.com/owner/repo-a", branch: "develop", files: ["src/A.java", "src/B.java", "db/V1.sql"] }),
    saveImplementationMappings: async (projectId, ruleUuid, input) => ({ ruleUuid, ...input }),
    ...overrides,
  };
  const tracked = Object.fromEntries(Object.entries(api).map(([name, method]) => [name, (...args) => { calls.push({ name, args }); return method(...args); }]));
  const hooks = {
    useState(initial) {
      const index = cursor++;
      slots[index] ??= { value: typeof initial === "function" ? initial() : initial };
      return [slots[index].value, update => {
        const next = typeof update === "function" ? update(slots[index].value) : update;
        if (!Object.is(next, slots[index].value)) { slots[index].value = next; changed = true; }
      }];
    },
    useRef(value) { const index = cursor++; slots[index] ??= { value: { current: value } }; return slots[index].value; },
    useEffect(effect, deps) {
      const index = cursor++;
      if (!slots[index] || deps.some((value, i) => !Object.is(value, slots[index].deps[i]))) {
        const previous = slots[index];
        slots[index] = { deps, cleanup: previous?.cleanup };
        pending.push(() => { previous?.cleanup?.(); slots[index].cleanup = effect(); });
      }
    },
  };
  const client = load(clientCode);
  const { ImplementationMappingsModal } = load(componentCode, { react: hooks, "../../shared/api/client": { ...client, rulesApi: tracked } });
  const environment = {
    document: { activeElement: null, body: { style: { overflow: "" } } },
    window: { confirm: () => { confirmations++; return confirmResult; } },
    HTMLElement: class {},
  };
  function render() {
    Object.assign(global, environment);
    let passes = 0;
    do {
      assert.ok(passes++ < 20, "render stabilizes");
      changed = false; cursor = 0;
      tree = ImplementationMappingsModal({ projects: projectData, initialProjectId: "project-a", onClose: () => closed++ });
      while (pending.length) pending.shift()();
    } while (changed);
  }
  render();
  return {
    calls,
    ApiClientError: client.ApiClientError,
    get tree() { return tree; },
    get text() { return text(tree); },
    get closed() { return closed; },
    get confirmations() { return confirmations; },
    confirm(value) { confirmResult = value; },
    async flush() { for (let i = 0; i < 4; i++) { await new Promise(resolve => setImmediate(resolve)); render(); } },
    button(label) { const node = find(tree, item => item.type === "button" && text(item) === label); assert.ok(node, `button ${label}`); return node; },
    click(label) { const node = this.button(label); assert.ok(!node.props.disabled, `${label} enabled`); node.props.onClick(); render(); },
    select(label, value) { const parent = find(tree, item => item.type === "label" && text(item).startsWith(label)); assert.ok(parent, label); const input = find(parent, item => item.type === "select"); assert.ok(!input.props.disabled); input.props.onChange({ target: { value } }); render(); },
    check(file, checked = true) { const label = find(tree, item => item.type === "label" && item.props.className === "repository-file-option" && text(item) === file); assert.ok(label, file); find(label, item => item.type === "input").props.onChange({ target: { checked } }); render(); },
    search(value) { find(tree, item => item.type === "input" && item.props.type === "search").props.onChange({ target: { value } }); render(); },
    remove(side, file) { const node = find(tree, item => item.props?.["aria-label"] === `Remove ${side} file ${file}`); assert.ok(node); node.props.onClick(); render(); },
    unmount() { for (const slot of slots) slot?.cleanup?.(); },
  };
}

async function selectRule(h, uuid = ruleA.id) { await h.flush(); h.select("Architecture Rule", uuid); await h.flush(); }
async function pick(h, button, files) { h.click(button); await h.flush(); for (const file of files) h.check(file); h.click("Add Selected Files"); }

test("loads saved arrays, preserves UUID selection, and reopens from backend state", async () => {
  const api = { implementationMappings: async (_, ruleUuid) => ({ ruleUuid, sourceFiles: ["saved/A.java"], targetFiles: ["saved/B.java"] }) };
  const h = mount(api);
  await selectRule(h);
  assert.match(h.text, /saved\/A.java/); assert.match(h.text, /saved\/B.java/);
  assert.match(h.text, /ADR-032/);
  assert.deepEqual(h.calls.find(call => call.name === "implementationMappings").args, ["project-a", ruleA.id]);
  assert.ok(h.button("Save Mappings").props.disabled);
  h.click("Close"); assert.equal(h.closed, 1); h.unmount();
  const reopened = mount(api); await selectRule(reopened);
  assert.match(reopened.text, /saved\/A.java/); assert.match(reopened.text, /saved\/B.java/); reopened.unmount();
});

test("searches paths, selects multiple files on both sides, deduplicates, and saves exact arrays", async () => {
  const h = mount(); await selectRule(h);
  h.click("Browse Source Files"); await h.flush();
  assert.match(h.text, /develop/);
  h.search("SRC/"); assert.ok(!h.text.includes("db/V1.sql"));
  h.check("src/A.java"); h.check("src/B.java"); h.click("Add Selected Files");
  await pick(h, "Browse Source Files", ["src/A.java"]);
  await pick(h, "Browse Target Files", ["src/A.java", "db/V1.sql"]);
  h.click("Save Mappings"); await h.flush();
  const call = h.calls.find(item => item.name === "saveImplementationMappings");
  assert.deepEqual(call.args, ["project-a", ruleA.id, { sourceFiles: ["src/A.java", "src/B.java"], targetFiles: ["db/V1.sql", "src/A.java"] }]);
  assert.equal(h.calls.filter(item => item.name === "repositoryFiles").length, 1);
  assert.match(h.text, /saved successfully/); assert.ok(h.button("Save Mappings").props.disabled); h.unmount();
});

test("generic browsing requires choosing the destination and Cancel preserves draft", async () => {
  const h = mount(); await selectRule(h);
  h.click("Browse Repository"); await h.flush(); h.check("src/A.java");
  assert.ok(h.button("Add Selected Files").props.disabled);
  h.select("Add files to", "targetFiles");
  assert.equal(h.confirmations, 0); assert.match(h.text, /1 files selected/);
  h.click("Cancel"); assert.ok(h.button("Save Mappings").props.disabled);
  h.click("Browse Repository"); await h.flush(); h.select("Add files to", "targetFiles"); h.check("db/V1.sql"); h.click("Add Selected Files");
  h.click("Save Mappings"); await h.flush();
  assert.deepEqual(h.calls.find(item => item.name === "saveImplementationMappings").args[2], { sourceFiles: [], targetFiles: ["db/V1.sql"] }); h.unmount();
});

test("removes files and saves empty arrays to clear all mappings", async () => {
  const h = mount({ implementationMappings: async (_, ruleUuid) => ({ ruleUuid, sourceFiles: ["src/A.java"], targetFiles: ["db/V1.sql"] }) });
  await selectRule(h); h.remove("source", "src/A.java"); h.remove("target", "db/V1.sql");
  h.click("Save Mappings"); await h.flush();
  assert.deepEqual(h.calls.find(item => item.name === "saveImplementationMappings").args[2], empty); h.unmount();
});

test("uses the successful response as saved state without leaking response UUID into later PUTs", async () => {
  const h = mount({ saveImplementationMappings: async (_, ruleUuid) => ({ ruleUuid, sourceFiles: ["normalized.java"], targetFiles: [] }) });
  await selectRule(h); await pick(h, "Browse Source Files", ["src/A.java"]);
  h.click("Save Mappings"); await h.flush();
  assert.match(h.text, /normalized.java/); assert.ok(!h.text.includes("src/A.java")); assert.ok(h.button("Save Mappings").props.disabled);
  h.remove("source", "normalized.java"); h.click("Save Mappings"); await h.flush();
  assert.deepEqual(h.calls.filter(item => item.name === "saveImplementationMappings")[1].args[2], empty); h.unmount();
});

test("failed save retains draft and backend field errors; save lock prevents double submissions", async () => {
  const pending = deferred();
  const h = mount({ saveImplementationMappings: () => pending.promise });
  await selectRule(h); await pick(h, "Browse Source Files", ["src/A.java"]);
  const save = h.button("Save Mappings").props.onClick; save(); save(); await h.flush();
  assert.equal(h.calls.filter(item => item.name === "saveImplementationMappings").length, 1);
  assert.ok(h.button("Saving...").props.disabled);
  pending.reject(new h.ApiClientError({ status: 400, message: "Validation failed", fieldErrors: { sourceFiles: "Path rejected by backend" } }));
  await h.flush(); assert.match(h.text, /src\/A.java/); assert.match(h.text, /Path rejected by backend/);
  assert.ok(!h.button("Save Mappings").props.disabled); h.unmount();
});

test("warns before discarding changes on project/rule switching and close", async () => {
  const h = mount({ list: async id => id === "project-a" ? [ruleA, makeRule("rule-other")] : [ruleB] });
  await selectRule(h); await pick(h, "Browse Source Files", ["src/A.java"]);
  h.confirm(false); h.select("Architecture Project", "project-b"); h.select("Architecture Rule", "rule-other"); h.click("Close");
  assert.equal(h.confirmations, 3); assert.equal(h.closed, 0); assert.match(h.text, /src\/A.java/);
  h.confirm(true); h.select("Architecture Project", "project-b"); await h.flush();
  assert.ok(!h.text.includes("src/A.java"));
  const ruleSelect = find(h.tree, item => item.type === "select" && React.Children.toArray(item.props.children).some(option => option.props?.value === ruleB.id));
  assert.equal(ruleSelect.props.value, "");
  assert.ok(!h.calls.some(item => item.name === "saveImplementationMappings")); h.unmount();
});

test("ignores stale mapping responses after selecting another rule", async () => {
  const old = deferred();
  const h = mount({ list: async () => [ruleA, makeRule("rule-new")], implementationMappings: async (_, uuid) => uuid === ruleA.id ? old.promise : { ruleUuid: uuid, sourceFiles: ["new.java"], targetFiles: [] } });
  await h.flush(); h.select("Architecture Rule", ruleA.id); await h.flush();
  h.select("Architecture Rule", "rule-new"); await h.flush(); old.resolve({ ruleUuid: ruleA.id, sourceFiles: ["stale.java"], targetFiles: [] }); await h.flush();
  assert.match(h.text, /new.java/); assert.ok(!h.text.includes("stale.java")); h.unmount();
});

test("ignores stale project rule lists and handles no-rule projects", async () => {
  const old = deferred();
  const h = mount({ list: async id => id === "project-a" ? old.promise : [] });
  h.select("Architecture Project", "project-b"); await h.flush(); old.resolve([ruleA]); await h.flush();
  assert.match(h.text, /no architecture rules/); assert.ok(!h.text.includes("OrderService")); assert.ok(h.button("Save Mappings").props.disabled); h.unmount();
});

test("missing repository URL explains configuration without blocking existing mapping removal", async () => {
  const h = mount({ implementationMappings: async (_, ruleUuid) => ({ ruleUuid, sourceFiles: ["old.java"], targetFiles: [] }) }, [{ ...projects[0], repositoryUrl: "" }]);
  await h.flush(); assert.match(h.text, /does not have a GitHub repository URL/);
  await selectRule(h); assert.ok(h.button("Browse Source Files").props.disabled);
  assert.equal(find(h.tree, item => item.type === "a").props.href, "/rules/projects");
  h.remove("source", "old.java"); h.click("Save Mappings"); await h.flush(); assert.match(h.text, /saved successfully/); h.unmount();
});

test("repository and mapping errors provide working retries", async () => {
  let mappingCalls = 0, repositoryCalls = 0;
  const h = mount({ implementationMappings: async (_, ruleUuid) => { if (++mappingCalls === 1) throw new Error("Mappings unavailable"); return { ruleUuid, ...empty }; }, repositoryFiles: async projectId => { if (++repositoryCalls === 1) throw new Error("GitHub rate limit reached"); return { projectId, repositoryUrl: projects[0].repositoryUrl, branch: "main", files: [] }; } });
  await selectRule(h); assert.match(h.text, /Mappings unavailable/); assert.ok(h.button("Save Mappings").props.disabled);
  h.click("Retry mappings"); await h.flush(); h.click("Browse Source Files"); await h.flush(); assert.match(h.text, /GitHub rate limit reached/);
  h.click("Retry browsing"); await h.flush(); assert.match(h.text, /No files were returned/); h.unmount();
});

test("typed API helpers use backend routes, UUIDs, PUT, and both path arrays", async () => {
  const previousFetch = global.fetch;
  const calls = [];
  global.fetch = async (url, options) => { calls.push({ url, ...options }); return { ok: true, json: async () => ({ ruleUuid: ruleA.id, ...empty }) }; };
  try {
    const { rulesApi } = load(clientCode);
    await rulesApi.repositoryFiles("project-a"); await rulesApi.implementationMappings("project-a", ruleA.id); await rulesApi.saveImplementationMappings("project-a", ruleA.id, empty);
    assert.equal(calls[0].url, "/api/rules/projects/project-a/repository/files");
    assert.equal(calls[1].url, `/api/rules/projects/project-a/rules/${ruleA.id}/implementation-mappings`);
    assert.equal(calls[2].url, calls[1].url); assert.equal(calls[2].method, "PUT"); assert.deepEqual(JSON.parse(calls[2].body), empty);
  } finally { global.fetch = previousFetch; }
});
