import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import test from "node:test";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const srcRoot = join(dirname(fileURLToPath(import.meta.url)), "..", "..");

function productionTsxFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return productionTsxFiles(path);
    }
    return entry.isFile() && entry.name.endsWith(".tsx") && !entry.name.endsWith(".test.tsx") ? [path] : [];
  });
}

function readSource(path: string): string {
  return readFileSync(path, "utf8");
}

test("AppShell exclusively owns the document main landmark and route title is not an h1", () => {
  const appShellSource = readSource(join(srcRoot, "components", "layout", "app-shell.tsx"));

  assert.equal(appShellSource.match(/<main\b/g)?.length, 1);
  assert.doesNotMatch(appShellSource, /<h1\b/);
});

test("PageHeader exclusively owns the page h1 landmark", () => {
  const pageHeaderPath = join(srcRoot, "components", "common", "page-header.tsx");
  const pageHeaderSource = readSource(pageHeaderPath);

  assert.equal(pageHeaderSource.match(/<h1\b/g)?.length, 1);
  assert.doesNotMatch(pageHeaderSource, /<main\b/);

  for (const file of productionTsxFiles(srcRoot)) {
    if (file === pageHeaderPath) {
      continue;
    }
    assert.doesNotMatch(readSource(file), /<h1\b/, `${relative(srcRoot, file)} must use PageHeader for its h1.`);
  }
});

test("all production TSX outside AppShell avoids nested main landmarks", () => {
  const appShellPath = join(srcRoot, "components", "layout", "app-shell.tsx");

  for (const file of productionTsxFiles(srcRoot)) {
    if (file === appShellPath) {
      continue;
    }
    const workspaceSource = readSource(file);

    assert.doesNotMatch(workspaceSource, /<\/?main\b/, `${relative(srcRoot, file)} must not render a main landmark.`);
  }
});
