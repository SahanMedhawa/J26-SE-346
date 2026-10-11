export async function getHealth(moduleName: string): Promise<string> {
  const response = await fetch(`/api/${moduleName}/health`);
  return response.text();
}
