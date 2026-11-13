import { defineConfig, loadEnv } from 'vite';
export default defineConfig(({mode}) => { const env=loadEnv(mode,'.','IPOLA_'); return {server:{host:'127.0.0.1',port:5173,strictPort:true,proxy:{'/api':env.IPOLA_API_TARGET || 'http://127.0.0.1:8080'}},preview:{host:'127.0.0.1',port:4173,strictPort:true}}; });
