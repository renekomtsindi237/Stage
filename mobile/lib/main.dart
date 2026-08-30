import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:microrecouv/l10n/app_localizations.dart';
import 'package:provider/provider.dart';

import 'core/config/app_config.dart';
import 'core/constants/app_theme.dart';
import 'core/providers/auth_provider.dart';
import 'core/providers/locale_provider.dart';
import 'core/providers/sync_provider.dart';
import 'core/providers/theme_provider.dart';
import 'core/services/agent_service.dart';
import 'core/services/alerte_service.dart';
import 'core/services/api_service.dart';
import 'core/services/auth_service.dart';
import 'core/services/client_service.dart';
import 'core/services/connectivity_service.dart';
import 'core/services/kpi_service.dart';
import 'core/services/local_database.dart';
import 'core/services/location_service.dart';
import 'core/services/notification_service.dart';
import 'core/services/offline_catalog_service.dart';
import 'core/services/sse_service.dart';
import 'core/services/storage_service.dart';
import 'core/services/sync_service.dart';
import 'router/app_router.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('fr_FR', null);
  await initializeDateFormatting('en_US', null);

  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
  ));

  final db = await LocalDatabase.instance();
  final storedUrl = await db.getKv('server_url');
  final storageService = StorageService();
  final apiService = ApiService(
    storageService,
    baseUrl: (storedUrl != null && storedUrl.isNotEmpty)
        ? storedUrl
        : AppConfig.compileTimeUrl,
  );
  final authService = AuthService(apiService, storageService);
  final notificationService = NotificationService();
  final connectivityService = ConnectivityService(
    healthCheck: apiService.pingHealth,
  );
  final locationService = LocationService(apiService, connectivityService, db);
  final clientService = ClientService(apiService, db, connectivityService);
  final agentService = AgentService(apiService, db, connectivityService);
  final alerteService = AlerteService(apiService, db, connectivityService);
  final catalog = OfflineCatalogService(
    clients: clientService,
    agent: agentService,
    alertes: alerteService,
    connectivity: connectivityService,
  );

  await notificationService.initialize();

  runApp(
    MultiProvider(
      providers: [
        Provider<LocalDatabase>.value(value: db),
        Provider<StorageService>.value(value: storageService),
        Provider<ApiService>.value(value: apiService),
        Provider<AuthService>.value(value: authService),
        Provider<NotificationService>.value(value: notificationService),
        Provider<KpiService>(create: (_) => KpiService(apiService)),
        Provider<AlerteService>.value(value: alerteService),
        Provider<ClientService>.value(value: clientService),
        Provider<AgentService>.value(value: agentService),
        Provider<ConnectivityService>.value(value: connectivityService),
        Provider<OfflineCatalogService>.value(value: catalog),
        ChangeNotifierProvider<LocationService>.value(value: locationService),
        Provider<SyncService>(create: (_) => SyncService(apiService, db)),
        Provider<SseService>(create: (_) => SseService(storageService, apiService)),
        ChangeNotifierProvider(
          create: (ctx) => SyncProvider(
            ctx.read<SyncService>(),
            ctx.read<SseService>(),
            ctx.read<ConnectivityService>(),
            catalog: ctx.read<OfflineCatalogService>(),
          ),
        ),
        ChangeNotifierProvider(create: (_) => ThemeProvider()),
        ChangeNotifierProvider(create: (_) => LocaleProvider()),
        ChangeNotifierProvider(
          create: (_) => AuthProvider(
            authService,
            locationService: locationService,
            catalog: catalog,
          ),
        ),
      ],
      child: const MicroRecouvApp(),
    ),
  );
}

class MicroRecouvApp extends StatelessWidget {
  const MicroRecouvApp({super.key});

  @override
  Widget build(BuildContext context) {
    final themeProvider = context.watch<ThemeProvider>();
    final localeProvider = context.watch<LocaleProvider>();
    final router = AppRouter.router;

    return MaterialApp.router(
      title: 'MicroRecouv',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: themeProvider.themeMode,
      locale: localeProvider.locale,
      supportedLocales: const [Locale('fr'), Locale('en')],
      localizationsDelegates: const [
        AppL10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      routerConfig: router,
    );
  }
}
