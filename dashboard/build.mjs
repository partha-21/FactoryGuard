import * as esbuild from 'esbuild';

await esbuild.build({
  entryPoints: ['src/main.jsx'],
  bundle: true,
  minify: true,
  jsx: 'automatic',
  target: 'es2019',
  define: { 'process.env.NODE_ENV': '"production"' },
  outfile: '../guard/src/main/resources/static/assets/app.js',
  logLevel: 'info',
});
