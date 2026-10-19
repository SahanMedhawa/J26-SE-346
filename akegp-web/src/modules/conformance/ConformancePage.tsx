import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import "./ConformancePage.css";

type CodeFile = { path: string; language: string; kind?: string };
type Dependency = { source: string; target: string };
type CodeGraph = { files: CodeFile[]; dependencies: Dependency[] };
type Node = { id: string; path: string; language: string; x: number; y: number; kind: string; members?: CodeFile[] };

function initialScanKey(projectId: string) {
	return `akegp.initial-scan.${projectId}`;
}

const colors: Record<string, string> = {
	Package: "#13b99a",
	Controller: "#4387ff",
	Service: "#00bd8b",
	Repository: "#ffa600",
	Model: "#e86c8d",
	Config: "#9d7cd8",
	Component: "#7dcfff",
	Hook: "#41a6b5",
	Utility: "#73daca",
	Middleware: "#bb9af7",
	Exception: "#f7768e",
	Build: "#cf8d48",
	Internal: "#8892a8",
	External: "#565f6e",
};

function nodeKind(file: CodeFile): string {
	// Use the backend-provided kind if available and meaningful
	if (file.kind && file.kind !== "Internal" && file.kind !== "External") return file.kind;
	if (file.language === "Maven" || file.language === "npm") return "Build";

	// Fallback heuristic: filename + path patterns
	const name = file.path.split("/").pop()?.toLowerCase() ?? "";
	const pathLower = file.path.toLowerCase();

	// Filename suffix conventions
	if (/controller|restcontroller|resource|endpoint/.test(name)) return "Controller";
	if (/service|serviceimpl|manager|provider|facade|usecase|orchestrator/.test(name)) return "Service";
	if (/repository|repositoryimpl|repo|dao|daoimpl|store|gateway|datasource/.test(name)) return "Repository";
	if (/entity|model|dto|vo|pojo|request|response|command|event/.test(name)) return "Model";
	if (/config|configuration|properties|settings/.test(name)) return "Config";
	if (/util|utils|utility|helper|helpers|constants|converter|mapper|adapter/.test(name)) return "Utility";
	if (/exception|error/.test(name) && !/handler/.test(name)) return "Exception";
	if (/filter|interceptor|middleware|guard|pipe/.test(name)) return "Middleware";
	if (/\.component\./.test(name) || /\.hook\./.test(name) || name.startsWith("use")) return "Component";

	// Dotted TS/JS naming conventions
	if (/\.controller\./.test(name) || /\.ctrl\./.test(name)) return "Controller";
	if (/\.service\./.test(name) || /\.svc\./.test(name)) return "Service";
	if (/\.repository\./.test(name) || /\.repo\./.test(name)) return "Repository";
	if (/\.route\./.test(name) || /\.routes\./.test(name) || /\.router\./.test(name)) return "Controller";

	// Directory path conventions
	if (/\/(controller|controllers|api|rest|endpoint|endpoints|routes?)\//.test(pathLower)) return "Controller";
	if (/\/(service|services|usecase|usecases)\//.test(pathLower)) return "Service";
	if (/\/(repository|repositories|repo|repos|dao|persistence)\//.test(pathLower)) return "Repository";
	if (/\/(model|models|entity|entities|domain|dto|dtos)\//.test(pathLower)) return "Model";
	if (/\/(config|configuration|configurations)\//.test(pathLower)) return "Config";
	if (/\/(util|utils|utility|helper|helpers|common|commons|shared|lib)\//.test(pathLower)) return "Utility";
	if (/\/(component|components|page|pages|view|views|screen|screens)\//.test(pathLower)) return "Component";
	if (/\/(hook|hooks)\//.test(pathLower)) return "Hook";
	if (/\/(middleware|middlewares|filter|filters|interceptor|interceptors|guard|guards)\//.test(pathLower)) return "Middleware";

	// Use backend kind if provided (even Internal)
	if (file.kind) return file.kind;
	return "Internal";
}

export function ConformancePage() {
	const location = useLocation();
	const { scanId } = useParams();
	const didAutoScan = useRef(false);
	const initialResult = location.state as { projectId?: string; repositoryId?: string; repositoryUrl?: string; initialScanId?: string; graph?: CodeGraph } | null;
	const projectId = initialResult?.projectId;
	const [repositoryId, setRepositoryId] = useState(initialResult?.repositoryId ?? "akegp");
	const [repositoryUrl, setRepositoryUrl] = useState(initialResult?.repositoryUrl ?? "");
	const [graph, setGraph] = useState<CodeGraph>(initialResult?.graph ?? { files: [], dependencies: [] });
	const [status, setStatus] = useState(initialResult?.graph ? "Scan complete" : "Idle");
	const [loading, setLoading] = useState(false);
	const [search, setSearch] = useState("");
	const [language, setLanguage] = useState("All Languages");
	const [layer, setLayer] = useState("All Layers");
	const [selectedPath, setSelectedPath] = useState("");
	const [scanOpen, setScanOpen] = useState(false);
	const [zoom, setZoom] = useState(1);

	const languages = useMemo(() => [...new Set(graph.files.map(file => file.language))].sort(), [graph.files]);
	const filteredFiles = useMemo(() => graph.files.filter(file => {
		const matchesLanguage = language === "All Languages" || file.language === language;
		const isBuild = file.language === "Maven" || file.language === "npm";
		const matchesLayer = layer === "All Layers" || (layer === "Packages" ? !isBuild : layer === "Files" ? !isBuild : isBuild);
		const matchesSearch = !search || file.path.toLowerCase().includes(search.toLowerCase());
		return matchesLanguage && matchesLayer && matchesSearch;
	}), [graph.files, language, layer, search]);
	const nodes = useMemo<Node[]>(() => {
		const sourceFiles = filteredFiles.filter(file => file.language !== "Maven" && file.language !== "npm");
		const packageGroups = new Map<string, CodeFile[]>([[".", sourceFiles]]);
		sourceFiles.forEach(file => {
			const folder = file.path.includes("/") ? file.path.slice(0, file.path.lastIndexOf("/")) : ".";
			let current = folder;
			while (current !== ".") {
				packageGroups.set(current, [...(packageGroups.get(current) ?? []), file]);
				const slash = current.lastIndexOf("/");
				current = slash < 0 ? "." : current.slice(0, slash);
			}
		});
		const includePackages = layer === "All Layers" || layer === "Packages";
		const includeFiles = layer !== "Packages";
		const packageNodes: Node[] = includePackages ? [...packageGroups.entries()].map(([path, members]) => ({ id: `package:${path}`, path, language: "Package", kind: "Package", members, x: 0, y: 0 })) : [];
		const fileNodes: Node[] = includeFiles ? filteredFiles.map(file => ({ ...file, id: `file:${file.path}`, kind: nodeKind(file), x: 0, y: 0 })) : [];
		const ordered = [...packageNodes, ...fileNodes];
		const columns = Math.max(1, Math.ceil(Math.sqrt(ordered.length)));
		return ordered.map((node, index) => ({ ...node, x: 100 + (index % columns) * 210 + (Math.floor(index / columns) % 2) * 60, y: 100 + Math.floor(index / columns) * 150 }));
	}, [filteredFiles, layer]);
	const canvasWidth = Math.max(900, Math.ceil(Math.sqrt(Math.max(1, nodes.length))) * 210 + 200);
	const canvasHeight = Math.max(620, Math.ceil(nodes.length / Math.max(1, Math.ceil(Math.sqrt(nodes.length)))) * 150 + 150);
	const selected = nodes.find(node => node.id === selectedPath) ?? nodes[0];
	const selectedPaths = new Set(selected?.kind === "Package" ? selected.members?.map(file => file.path) ?? [] : selected ? [selected.path] : []);
	const inboundPaths = new Set(graph.dependencies.filter(edge => selectedPaths.has(edge.target) && !selectedPaths.has(edge.source)).map(edge => edge.source));
	const outboundPaths = new Set(graph.dependencies.filter(edge => selectedPaths.has(edge.source) && !selectedPaths.has(edge.target)).map(edge => edge.target));
	const selectedInbound = graph.files.filter(file => inboundPaths.has(file.path));
	const selectedOutbound = graph.files.filter(file => outboundPaths.has(file.path));
	const dependencyCount = graph.dependencies.filter(edge => nodes.some(node => node.id === `file:${edge.source}`) && nodes.some(node => node.id === `file:${edge.target}`)).length;

	async function runScan(id = repositoryId, url = repositoryUrl) {
		setRepositoryId(id); setRepositoryUrl(url); setLoading(true); setStatus("Scanning repository...");
		try {
			const response = await fetch("/api/conformance/graphs/import", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ repositoryId: id, repositoryUrl: url }) });
			if (!response.ok) throw new Error(await response.text());
			const result = await response.json() as { scan: { scanId: string }; graph: CodeGraph };
			setGraph(result.graph); setSelectedPath(""); setStatus("Scan complete"); setScanOpen(false);
			if (projectId) window.localStorage.setItem(initialScanKey(projectId), result.scan.scanId);
		} catch (error) { setStatus(error instanceof Error ? error.message : "The repository could not be imported."); }
		finally { setLoading(false); }
	}
	function importRepository(event: FormEvent) { event.preventDefault(); void runScan(); }
	useEffect(() => {
		const scan = location.state as { projectId?: string; repositoryId?: string; repositoryUrl?: string; initialScanId?: string } | null;
		if (didAutoScan.current || !scan?.repositoryUrl || scan.initialScanId) return;
		didAutoScan.current = true;
		void runScan(scan.repositoryId || "akegp", scan.repositoryUrl);
	}, [location.state]);
	useEffect(() => {
		if (!projectId || initialResult?.graph || !initialResult?.initialScanId) return;
		setLoading(true); setStatus("Loading initial scan...");
		void fetch(`/api/conformance/graphs/${initialResult.initialScanId}`)
			.then(async response => { if (!response.ok) throw new Error("The initial scan could not be loaded."); return response.json() as Promise<CodeGraph>; })
			.then(savedGraph => { setGraph(savedGraph); setStatus("Initial scan loaded"); })
			.catch(error => { window.localStorage.removeItem(initialScanKey(projectId)); setStatus(error instanceof Error ? error.message : "The initial scan could not be loaded."); })
			.finally(() => setLoading(false));
	}, [initialResult?.graph, initialResult?.initialScanId, projectId]);
	useEffect(() => {
		if (!scanId || initialResult?.graph) return;
		setLoading(true);
		setStatus("Loading saved scan...");
		void fetch(`/api/conformance/graphs/${scanId}`)
			.then(async response => {
				if (!response.ok) throw new Error("The saved scan could not be loaded.");
				return response.json() as Promise<CodeGraph>;
			})
			.then(savedGraph => { setGraph(savedGraph); setStatus("Scan loaded"); })
			.catch(error => setStatus(error instanceof Error ? error.message : "The saved scan could not be loaded."))
			.finally(() => setLoading(false));
	}, [scanId]);

	function exportGraph() {
		const svg = document.querySelector<SVGSVGElement>(".dependency-canvas svg");
		if (!svg) return;
		const data = new XMLSerializer().serializeToString(svg);
		const url = URL.createObjectURL(new Blob([data], { type: "image/svg+xml;charset=utf-8" }));
		const link = document.createElement("a"); link.href = url; link.download = `${repositoryId || "dependency-graph"}.svg`; link.click(); URL.revokeObjectURL(url);
	}

	const byPath = new Map(nodes.map(node => [node.id, node]));
	const packageNodes = nodes.filter(node => node.kind === "Package");
	const packageEdges = [
		...packageNodes.flatMap(node => {
			if (node.path === ".") return [];
			const slash = node.path.lastIndexOf("/");
			const parent = slash < 0 ? "." : node.path.slice(0, slash);
			return [{ source: `package:${parent}`, target: node.id }];
		}),
		...packageNodes.flatMap(node => (node.members ?? [])
			.filter(file => (file.path.includes("/") ? file.path.slice(0, file.path.lastIndexOf("/")) : ".") === node.path)
			.map(file => ({ source: node.id, target: `file:${file.path}` }))),
	];
	const totalEdges = dependencyCount + packageEdges.filter(edge => byPath.has(edge.source) && byPath.has(edge.target)).length;
	return <main className="graph-app">
				<header className="graph-topbar"><div className="graph-search"><span>⌕</span><input aria-label="Search components" placeholder="Search components..." value={search} onChange={event => setSearch(event.target.value)} /></div><div className="topbar-actions"><Link className="new-scan-button" to="/repository">+ New scan</Link><span className="topbar-status">● {status}</span><span className="avatar">◈</span></div></header>
		<div className="graph-layout">
			
			<section className="graph-workspace"><div className="graph-toolbar"><div className="breadcrumbs"><Link className="graph-home-button" to="/">Home</Link><b>›</b><strong>Dependency Graphs</strong></div><div className="toolbar-controls"><div className="zoom-control"><button aria-label="Zoom out" onClick={() => setZoom(value => Math.max(.65, value - .1))}>−</button><button aria-label="Zoom in" onClick={() => setZoom(value => Math.min(1.5, value + .1))}>＋</button></div><select aria-label="Filter by language" value={language} onChange={event => setLanguage(event.target.value)}><option>All Languages</option>{languages.map(item => <option key={item}>{item}</option>)}</select><select aria-label="Filter by layer" value={layer} onChange={event => setLayer(event.target.value)}><option>All Layers</option><option>Packages</option><option>Files</option><option>Build</option></select><button className="export-button" onClick={exportGraph}>⇩ Export Graph</button><button className="scan-button" onClick={() => setScanOpen(value => !value)}>▶ Run Full Scan</button></div></div>
				{scanOpen && <form className="scan-form" onSubmit={importRepository}><input aria-label="Graph ID" placeholder="Graph ID" value={repositoryId} onChange={event => setRepositoryId(event.target.value)} required/><input aria-label="GitHub repository URL" placeholder="https://github.com/owner/repository" value={repositoryUrl} onChange={event => setRepositoryUrl(event.target.value)} required/><button disabled={loading}>{loading ? "Scanning..." : "Start scan"}</button></form>}
				<div className="graph-metrics"><div><small>TOTAL NODES</small><strong>{nodes.length.toLocaleString()}</strong></div><div><small>TOTAL EDGES</small><strong>{totalEdges.toLocaleString()}</strong></div></div>
				<div className="graph-content"><section className="dependency-canvas" aria-label="Dependency graph diagram"><svg viewBox={`0 0 ${canvasWidth} ${canvasHeight}`} role="img" aria-label="Repository dependency graph" style={{ transform: `scale(${zoom})` }}><defs><marker id="edge-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L7,3 z" fill="#66717b"/></marker></defs>
					{graph.dependencies.map((edge, index) => { const source = byPath.get(`file:${edge.source}`); const target = byPath.get(`file:${edge.target}`); return source && target ? <line key={`${edge.source}-${edge.target}-${index}`} x1={source.x} y1={source.y} x2={target.x} y2={target.y} className="graph-edge" markerEnd="url(#edge-arrow)"/> : null; })}
					{packageEdges.map((edge, index) => { const source = byPath.get(edge.source); const target = byPath.get(edge.target); return source && target ? <line key={`contains-${index}`} x1={source.x} y1={source.y} x2={target.x} y2={target.y} className="package-edge"/> : null; })}
					{nodes.map(node => <g key={node.id} className={`graph-node ${node.kind === "Package" ? "package-node" : ""} ${selected?.id === node.id ? "selected" : ""}`} onClick={() => setSelectedPath(node.id)} role="button" tabIndex={0} onKeyDown={event => { if (event.key === "Enter") setSelectedPath(node.id); }}><title>{node.path}</title>{node.kind === "Package" ? <rect x={node.x - 25} y={node.y - 18} width="50" height="36" rx="8" stroke={colors.Package}/> : <circle cx={node.x} cy={node.y} r="16" stroke={colors[node.kind] ?? colors.Internal}/>}<text className="node-name" x={node.x} y={node.y - 28} textAnchor="middle">{node.kind === "Package" ? (node.path === "." ? "root package" : node.path.split("/").pop()) : node.path.split("/").pop()}</text><text className="node-language" x={node.x} y={node.y + 38} textAnchor="middle">{node.kind === "Package" ? `${node.members?.length ?? 0} files` : `${node.kind} · ${node.language}`}</text></g>)}
					{!nodes.length && <text className="empty-graph" x={canvasWidth / 2} y={canvasHeight / 2} textAnchor="middle">Import a repository to explore its dependencies</text>}</svg>
					<div className="node-legend"><strong>NODE TYPES</strong><div>{[...new Set(nodes.map(node => node.kind))].sort().map(kind => <span key={kind}><i style={{ background: colors[kind] ?? colors.Internal }}/>{kind}</span>)}</div></div>
				</section><aside className="node-details">{selected ? <><section className="selected-summary"><span className="detail-eyebrow"><i style={{ background: colors[selected.kind] ?? colors.Internal }}/>{selected.kind.toUpperCase()} LAYER</span><h2>{selected.kind === "Package" && selected.path === "." ? "Repository root" : selected.path.split("/").pop()}</h2><code>{selected.path}</code></section><section className="detail-section"><h3>Dependency Metrics</h3><div className="dependency-metrics"><div><small>Inbound<br/>(Used By)</small><strong>{selectedInbound.length}</strong></div><div><small>Outbound<br/>(Uses)</small><strong>{selectedOutbound.length}</strong></div></div></section><section className="detail-section connected-files"><h3>{selected.kind === "Package" ? `Package Contents (${selected.members?.length ?? 0})` : "Connected Files"}</h3>{selected.kind === "Package" && (selected.members ?? []).slice(0, 8).map(file => <p key={file.path}>{file.path.split("/").pop()}<small>{file.path}</small></p>)}<h4>Inbound Dependencies</h4>{selectedInbound.slice(0, 5).map(file => <p key={`in-${file.path}`}>{file.path.split("/").pop()}<small>{file.path}</small></p>)}<h4>Outbound Dependencies</h4>{selectedOutbound.slice(0, 5).map(file => <p key={`out-${file.path}`}>{file.path.split("/").pop()}<small>{file.path}</small></p>)}{!selectedInbound.length && !selectedOutbound.length && selected.kind !== "Package" && <p className="muted">No connected files</p>}</section></> : <div className="details-empty">Select a node to view dependency details.</div>}</aside></div>
				<footer className="graph-footer"><span>Scan Status: {status}</span><div><a href="#">Documentation</a><a href="#">Support</a><a href="#">API</a></div></footer>
			</section>
		</div>
	</main>;
}

