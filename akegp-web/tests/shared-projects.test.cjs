// Run: node --test tests/*.test.cjs. Mocked component/routing checks; no live writes or browser.
const { test, before } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
const compiled = {};
before(async () => {
  const { transformWithOxc } = await import("vite");
  for (const relative of ["shared/projects/ArchitectureProjects.tsx", "shared/projects/SelectedProject.tsx", "shared/api/client.ts", "modules/rules/RulesPage.tsx", "modules/rules/ArchitectureRulesPage.tsx", "modules/rules/RuleValidationPage.tsx", "modules/conformance/HomePage.tsx", "app/App.tsx"]) {
    const filename = path.join(__dirname, "../src", relative);
    compiled[relative] = (await transformWithOxc(fs.readFileSync(filename, "utf8"), filename, { jsx: { runtime: "automatic" } })).code;
  }
});
function load(relative, imports = {}) {
  let code = compiled[relative]; const names = [];
  code = code.replace(/import\s*['"][^'"]+\.css['"];?/g, "");
  code = code.replace(/import\s*\{([^}]+)\}\s*from\s*(['"][^'"]+['"]);/g, (_, items, source) => `const {${items.replace(/\s+as\s+/g, ": ")}} = require(${source});`);
  code = code.replace(/export (async )?(function|const|class) (\w+)/g, (_, async, kind, name) => { names.push(name); return `${async || ""}${kind} ${name}`; });
  const module = { exports: {} };
  new Function("require", "module", "exports", `${code}\nmodule.exports = {${names.join(",")}};`)(name => name in imports ? imports[name] : require(name), module, module.exports);
  return module.exports;
}
const projectA = { id: "project-a", name: "HealthMate", description: "Healthcare architecture", repositoryUrl: "https://github.com/owner/healthmate", architectureStyles: ["MICROSERVICES"], registeredAt: "2026-10-18T10:00:00Z", status: "ACTIVE" };
const projectB = { ...projectA, id: "project-b", name: "BookNest", description: "Library architecture", status: "INACTIVE" };
function text(node) { if (node == null || typeof node === "boolean") return ""; if (typeof node !== "object") return String(node); return React.Children.toArray(node.props?.children).map(text).join(""); }
function find(node, predicate) {
  if (!node || typeof node !== "object") return;
  if (predicate(node)) return node;
  for (const child of React.Children.toArray(node.props?.children)) { const result = find(child, predicate); if (result) return result; }
}
function architectureStyleSelect(h) {
  const form = h.element("ProjectForm");
  const select = find(form.type(form.props), node => node.type === "select" && node.props.name === "architectureStyle");
  const choices = React.Children.toArray(select.props.children).filter(option => !option.props.disabled);
  assert.deepEqual(choices.map(option => [option.props.value, text(option)]), [["MICROSERVICES", "Microservices"], ["LAYERED", "Layered"]]);
  return select;
}
function selection(initial = null) {
  let selectedProject = initial;
  const setSelectedProject = project => { selectedProject = project; };
  return { get: () => ({ selectedProject, setSelectedProject }) };
}

function mount(name = "ArchitectureProjects", options = {}) {
  const slots = [], pending = [], calls = [];
  let cursor = 0, changed = false, tree;
  const selected = options.selection ?? selection();
  const data = [...(options.projects ?? [projectA, projectB])];
  const client = load("shared/api/client.ts");
  const implementations = {
    list: async () => [...data], get: async id => data.find(item => item.id === id),
    create: async input => { const saved = { ...projectA, ...input, id: "new-project" }; data.push(saved); return saved; },
    update: async (id, input) => { const index = data.findIndex(item => item.id === id); data[index] = { ...data[index], ...input }; return data[index]; },
    updateStatus: async (id, status) => { const index = data.findIndex(item => item.id === id); data[index] = { ...data[index], status }; return data[index]; },
    ...options.api,
  };
  const projectsApi = Object.fromEntries(Object.entries(implementations).map(([method, fn]) => [method, (...args) => { calls.push({ method, args }); return fn(...args); }]));
  const hooks = {
    ...React,
    useState(initial) {
      const index = cursor++; slots[index] ??= { value: typeof initial === "function" ? initial() : initial };
      return [slots[index].value, update => { const value = typeof update === "function" ? update(slots[index].value) : update; if (!Object.is(value, slots[index].value)) { slots[index].value = value; changed = true; } }];
    },
    useRef(value) { const index = cursor++; slots[index] ??= { value: { current: value } }; return slots[index].value; },
    useMemo(fn, deps) { const index = cursor++; if (!slots[index] || deps.some((value, i) => !Object.is(value, slots[index].deps[i]))) slots[index] = { value: fn(), deps }; return slots[index].value; },
    useEffect(effect, deps) { const index = cursor++; if (!slots[index] || deps.some((value, i) => !Object.is(value, slots[index].deps[i]))) { const previous = slots[index]; slots[index] = { deps, cleanup: previous?.cleanup }; pending.push(() => { previous?.cleanup?.(); slots[index].cleanup = effect(); }); } },
  };
  const shared = { useSelectedProject: () => selected.get() };
  const empty = () => null;
  const router = { Link: empty, NavLink: empty, Outlet: empty, useNavigate: () => empty, useSearchParams: () => [new URLSearchParams()] };
  const api = { ...client, projectsApi, adrsApi: { ...client.adrsApi, list: async id => { calls.push({ method: "adrs", args: [id] }); return []; } }, rulesApi: { ...client.rulesApi, list: async id => { calls.push({ method: "rules", args: [id] }); return []; } } };
  const imports = { react: hooks, "react-router-dom": router, "../api/client": api, "../../shared/api/client": api, "./SelectedProject": shared, "./rulesWorkspace": shared, "./ImplementationMappingsModal": { ImplementationMappingsModal: empty }, "./ArchitectureRulesPage": { DarkSelect: empty }, "./RuleAssurance": { ArchitectReviewPanel: empty, StoredValidationResult: empty, ArchitectConfirmationRegister: empty } };
  const files = { ArchitectureProjects: "shared/projects/ArchitectureProjects.tsx", SelectedProjectProvider: "shared/projects/SelectedProject.tsx", RulesPage: "modules/rules/RulesPage.tsx", ADRManagementPage: "modules/rules/RulesPage.tsx", ArchitectureRulesPage: "modules/rules/ArchitectureRulesPage.tsx", RuleValidationPage: "modules/rules/RuleValidationPage.tsx" };
  const Component = load(files[name], imports)[name];
  function render() { let passes = 0; do { assert.ok(passes++ < 25, "renders stabilize"); cursor = 0; changed = false; tree = Component({ children: "route content" }); while (pending.length) pending.shift()(); } while (changed); }
  render();
  return {
    calls, data, router, ApiClientError: client.ApiClientError,
    get tree() { return tree; }, get text() { return text(tree); },
    element(name) { const node = find(tree, item => item.type?.name === name); assert.ok(node, name); return node; },
    button(label, id) { const scope = id ? find(tree, node => node.type === "tr" && String(node.key).endsWith(`$${id}`)) : tree; assert.ok(scope, id); const button = find(scope, node => node.type === "button" && text(node).trim().replace(/^\+\s*/, "") === label); assert.ok(button, label); return button; },
    click(label, id) { this.button(label, id).props.onClick(); render(); },
    search(value) { find(tree, node => node.type === "input" && node.props.type === "search").props.onChange({ target: { value } }); render(); },
    filter(value) { find(tree, node => node.type === "select").props.onChange({ target: { value } }); render(); },
    async submit(values) { const old = global.FormData; global.FormData = class { get(name) { return values[name] ?? null; } }; try { this.element("ProjectForm").props.onSubmit({ preventDefault() {}, currentTarget: {} }); } finally { global.FormData = old; } render(); await this.flush(); },
    update() { render(); },
    async flush() { for (let i = 0; i < 4; i++) { await new Promise(resolve => setImmediate(resolve)); render(); } },
    unmount() { for (const slot of slots) slot?.cleanup?.(); },
  };
}

test("shared project table displays saved records and retains search/status filters", async () => {
  const h = mount(); await h.flush(); assert.match(h.text, /HealthMate/); assert.match(h.text, /BookNest/);
  h.search("book"); assert.match(h.text, /BookNest/); assert.ok(!h.text.includes("HealthMate"));
  h.filter("ACTIVE"); assert.match(h.text, /No matching projects/);
  h.search(""); assert.match(h.text, /HealthMate/); assert.ok(!h.text.includes("BookNest"));
  assert.equal(h.calls.filter(call => call.method === "list").length, 1); h.unmount();
});

test("dedicated projects registration retains form validation and sends the existing create payload", async () => {
  const h = mount(); await h.flush(); h.click("Register Project");
  const form = h.element("ProjectForm"); const html = renderToStaticMarkup(React.createElement(form.type, form.props));
  for (const name of ["projectName", "architectureStyle", "repositoryUrl"]) assert.match(html.match(new RegExp(`<[^>]*name="${name}"[^>]*>`))[0], /required/);
  assert.match(html, /type="url"/);
  assert.equal(architectureStyleSelect(h).props.defaultValue, "");
  await h.submit({ projectName: "  New System  ", architectureStyle: "LAYERED", description: "  Description  ", repositoryUrl: "https://github.com/owner/new" });
  assert.deepEqual(h.calls.find(call => call.method === "create").args, [{ name: "New System", architectureStyles: ["LAYERED"], description: "Description", repositoryUrl: "https://github.com/owner/new" }]);
  assert.match(h.text, /New System/); assert.match(h.text, /Project registered successfully/); h.unmount();
});

test("View Details and Select Project share the actual saved project UUID", async () => {
  const shared = selection(); const h = mount("ArchitectureProjects", { selection: shared }); await h.flush();
  h.click("View Details", projectB.id); await h.flush();
  const details = h.element("ProjectDetails"); assert.equal(details.props.project.id, projectB.id);
  const markup = renderToStaticMarkup(React.createElement(details.type, details.props)); assert.match(markup, /BookNest/); assert.match(markup, /Git repository/);
  details.props.onSelect(); h.update(); assert.equal(shared.get().selectedProject.id, projectB.id); assert.match(h.text, /Project selected/); h.unmount();
});

test("Edit and deactivate/reactivate preserve IDs and synchronize the shared selected project", async () => {
  const shared = selection(projectA); const h = mount("ArchitectureProjects", { selection: shared }); await h.flush();
  h.click("Edit", projectA.id); assert.equal(h.element("ProjectForm").props.project.id, projectA.id);
  assert.equal(architectureStyleSelect(h).props.defaultValue, "MICROSERVICES");
  await h.submit({ projectName: "HealthMate Updated", architectureStyle: "LAYERED", description: "Updated", repositoryUrl: projectA.repositoryUrl });
  assert.equal(h.calls.find(call => call.method === "update").args[0], projectA.id); assert.equal(shared.get().selectedProject.name, "HealthMate Updated");
  assert.deepEqual(h.calls.find(call => call.method === "update").args[1].architectureStyles, ["LAYERED"]);
  h.click("Deactivate", projectA.id); h.element("ConfirmationDialog").props.onConfirm(); h.update(); await h.flush();
  assert.equal(shared.get().selectedProject.status, "INACTIVE"); h.click("Activate", projectA.id); await h.flush();
  assert.equal(shared.get().selectedProject.status, "ACTIVE"); assert.equal(shared.get().selectedProject.id, projectA.id);
  assert.deepEqual(h.calls.filter(call => call.method === "updateStatus").map(call => call.args), [[projectA.id, "INACTIVE"], [projectA.id, "ACTIVE"]]); h.unmount();
});

test("legacy combined styles remain visible and are preserved unless explicitly replaced", async () => {
  for (const replacement of [undefined, "MICROSERVICES", "LAYERED"]) {
    const legacy = { ...projectA, architectureStyles: ["MICROSERVICES_AND_LAYERED"] };
    const shared = selection(legacy);
    const h = mount("ArchitectureProjects", { projects: [legacy], selection: shared }); await h.flush();
    assert.match(h.text, /Microservices \+ Layered/);
    h.click("Edit", legacy.id);
    const select = architectureStyleSelect(h);
    assert.equal(select.props.defaultValue, ""); assert.equal(select.props.required, false);
    const form = h.element("ProjectForm");
    const markup = renderToStaticMarkup(React.createElement(form.type, form.props));
    assert.match(markup, /Existing style: Microservices \+ Layered/);
    assert.match(markup, /preserved unless you select a different style/);
    assert.ok(!markup.includes('value="MICROSERVICES_AND_LAYERED"'));
    await h.submit({ projectName: "Legacy Updated", architectureStyle: replacement, description: "Edited description", repositoryUrl: legacy.repositoryUrl });
    const expected = replacement ? [replacement] : legacy.architectureStyles;
    assert.deepEqual(h.calls.find(call => call.method === "update").args, [legacy.id, { name: "Legacy Updated", description: "Edited description", repositoryUrl: legacy.repositoryUrl, architectureStyles: expected }]);
    assert.deepEqual(shared.get().selectedProject.architectureStyles, expected);
    assert.match(h.text, /Project updated successfully/); h.unmount();
  }
});

test("project backend errors keep the form available and the selected project intact", async () => {
  const shared = selection(projectA); let h;
  h = mount("ArchitectureProjects", { selection: shared, api: { create: async () => { throw new h.ApiClientError({ status: 400, message: "Validation failed", fieldErrors: { name: "Project name already exists" } }); } } });
  await h.flush(); h.click("Register Project"); await h.submit({ projectName: "HealthMate", architectureStyle: "LAYERED", repositoryUrl: projectA.repositoryUrl });
  assert.match(h.text, /Project name already exists/); assert.equal(h.element("ProjectForm").props.busy, false); assert.equal(shared.get().selectedProject.id, projectA.id); h.unmount();
});

test("one app-level provider preserves selection through projects, Home and workspace navigation", async () => {
  const provider = mount("SelectedProjectProvider");
  const shared = { get: () => provider.tree.props.value };
  const home = mount("ArchitectureProjects", { selection: shared }); await home.flush();
  home.click("View Details", projectB.id); await home.flush(); home.element("ProjectDetails").props.onSelect(); await provider.flush(); home.unmount();
  const workspace = mount("RulesPage", { selection: shared }); assert.match(workspace.text, /BookNest/); assert.ok(!workspace.text.includes("Architecture Projects"));
  assert.match(workspace.text, /ADR Management/); assert.match(workspace.text, /Architecture Rule Engineering/); assert.match(workspace.text, /Rule Validation/); workspace.unmount();
  const returnedHome = mount("ArchitectureProjects", { selection: shared }); await returnedHome.flush(); await provider.flush();
  assert.equal(provider.tree.props.value.selectedProject.id, projectB.id); returnedHome.unmount(); provider.unmount();
});

test("ADR, rule engineering and validation prefer the Home selection over the first project", async () => {
  const selected = { ...projectB, status: "ACTIVE" }; const shared = selection(selected);
  const options = { selection: shared, projects: [projectA, selected] };
  const adrPage = mount("ADRManagementPage", options); await adrPage.flush();
  assert.ok(adrPage.calls.some(call => call.method === "adrs" && call.args[0] === selected.id));
  adrPage.click("Create ADR"); assert.equal(adrPage.element("ADRForm").props.projectId, selected.id); adrPage.unmount();
  const rulesPage = mount("ArchitectureRulesPage", options); await rulesPage.flush();
  assert.ok(rulesPage.calls.some(call => call.method === "rules" && call.args[0] === selected.id)); rulesPage.unmount();
  const validation = mount("RuleValidationPage", options); await validation.flush(); assert.equal(validation.element("ProjectValidation").props.project.id, selected.id); validation.unmount();
});

test("Home has a top projects link and retains existing content without embedding project management", () => {
  const Link = () => null;
  const { HomePage } = load("modules/conformance/HomePage.tsx", { "react-router-dom": { Link } });
  const tree = HomePage(); const children = React.Children.toArray(tree.props.children);
  assert.equal(children[0].type, "header"); assert.equal(children[1].type, "nav"); assert.equal(children[2].props.className, "home-hero"); assert.equal(children.at(-1).type, "footer");
  const projectsLink = find(children[1], node => node.type === Link && node.props.to === "/projects");
  assert.ok(projectsLink); assert.match(text(projectsLink), /Architecture Projects/); assert.match(text(projectsLink), /Register and manage architecture projects/);
  assert.ok(!find(tree, node => node.type === "table" || node.type?.name === "ArchitectureProjects"));
  assert.ok(!text(tree).includes("Register Project"));
  for (const title of ["Keep your architecture", "Govern architecture through the pipeline.", "From architecture intent to code.", "Start a conformance scan"]) assert.ok(text(tree).includes(title));
  for (const href of ["/rules", "/conformance", "/enforcement", "/analytics", "/settings"]) assert.ok(find(tree, node => node.type === Link && node.props.to === href));
});

test("dedicated project management includes Back to Home and the full saved projects interface", async () => {
  const h = mount(); await h.flush();
  assert.equal(h.tree.type, "main");
  const back = find(h.tree, node => node.type === h.router.Link && node.props.to === "/home");
  assert.ok(back); assert.match(text(back), /Back to Home/);
  assert.ok(find(h.tree, node => node.type === "table")); assert.match(h.text, /HealthMate/); assert.match(h.text, /BookNest/);
  assert.ok(h.button("Register Project")); h.unmount();
});

test("routes share one provider, mount the reused manager at /projects, and redirect the legacy URL", () => {
  const empty = () => null;
  const Provider = () => null, Home = () => null, Projects = () => null, Route = () => null, Navigate = () => null;
  const imports = {
    "react-router-dom": { Link: empty, Navigate, Route, Routes: empty, useLocation: () => ({ pathname: "/home" }) },
    "../shared/projects/SelectedProject": { SelectedProjectProvider: Provider },
    "../shared/projects/ArchitectureProjects": { ArchitectureProjects: Projects },
    "../modules/conformance/HomePage": { HomePage: Home },
    "../modules/rules/RulesPage": { RulesPage: empty, ADRManagementPage: empty },
    "../modules/rules/ArchitectureRulesPage": { ArchitectureRulesPage: empty }, "../modules/rules/RuleValidationPage": { RuleValidationPage: empty },
    "../modules/analytics/AnalyticsPage": { AnalyticsPage: empty }, "../modules/conformance/ConformancePage": { ConformancePage: empty }, "../modules/conformance/RepositoryPage": { RepositoryPage: empty }, "../modules/conformance/SettingsPage": { SettingsPage: empty }, "../modules/enforcement/EnforcementPage": { EnforcementPage: empty },
  };
  const tree = load("app/App.tsx", imports).App(); assert.equal(tree.type, Provider);
  assert.equal(find(tree, node => node.type === Route && node.props.path === "/home").props.element.type, Home);
  assert.equal(find(tree, node => node.type === Route && node.props.path === "/projects").props.element.type, Projects);
  const rules = find(tree, node => node.type === Route && node.props.path === "/rules");
  const redirect = find(rules, node => node.type === Route && node.props.path === "projects").props.element;
  assert.equal(redirect.type, Navigate); assert.equal(redirect.props.to, "/projects"); assert.equal(redirect.props.replace, true);
  for (const relative of ["shared/projects/ArchitectureProjects.tsx", "shared/projects/SelectedProject.tsx"]) assert.ok(!/from\s*["'][^"']*(modules\/rules|rulesWorkspace)/.test(fs.readFileSync(path.join(__dirname, "../src", relative), "utf8")));
});
