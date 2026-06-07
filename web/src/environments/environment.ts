// Environnement développement — proxy.conf.json redirige /api → localhost:8080
export const environment = {
  production: false,
  staging:    false,
  apiUrl:     '',
  appName:    'MicroRecouv',
  appVersion: '1.0.0',
  sentryDsn:  '',
  logLevel:   'debug',
};
