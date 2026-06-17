import { fileURLToPath, URL } from 'node:url';
import { createRequire } from 'node:module';
import * as ts from 'typescript';

process.env.ESBUILD_BINARY_PATH = fileURLToPath(
  new URL('./node_modules/@esbuild/win32-x64/esbuild.exe', import.meta.url),
);

const require = createRequire(import.meta.url);
const childProcess = require('node:child_process');
const originalExec = childProcess.exec;
childProcess.exec = ((command, options, callback) => {
  if (typeof options === 'function') {
    callback = options;
    options = undefined;
  }

  if (typeof command === 'string' && command.trim().toLowerCase() === 'net use') {
    if (typeof callback === 'function') {
      queueMicrotask(() => callback(null, '', ''));
    }
    return {
      kill() {},
    };
  }

  return originalExec(command, options, callback);
});

const tsTranspilePlugin = {
  name: 'ts-transpile',
  enforce: 'pre',
  transform(code, id) {
    const filePath = id.split('?', 1)[0];

    // Skip node_modules and .d.ts files
    if (filePath.includes('node_modules') || filePath.endsWith('.d.ts')) {
      return null;
    }

    if (!/\.(ts|tsx|js|jsx|mts|cts)$/.test(filePath)) {
      return null;
    }

    const result = ts.transpileModule(code, {
      compilerOptions: {
        target: ts.ScriptTarget.ES2020,
        module: ts.ModuleKind.ESNext,
        jsx: ts.JsxEmit.ReactJSX,
        esModuleInterop: true,
        allowSyntheticDefaultImports: true,
        sourceMap: true,
      },
      fileName: filePath,
    });

    return {
      code: result.outputText,
      map: result.sourceMapText ?? null,
    };
  },
};

const { createServer } = await import('vite');
const react = (await import('@vitejs/plugin-react')).default;

const port = Number(process.env.PORT ?? 5173);

const server = await createServer({
  configFile: false,
  root: process.cwd(),
  plugins: [tsTranspilePlugin, react()],
  esbuild: {
    include: /\.js$/,
    exclude: [],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port,
    strictPort: false,
    proxy: {
      '/functions': {
        target: 'https://us-central1-flamingo-ea5e5.cloudfunctions.net',
        changeOrigin: true,
        secure: true,
        rewrite: (path) => path.replace(/^\/functions/, ''),
      },
    },
  },
});

await server.listen();
server.printUrls();
