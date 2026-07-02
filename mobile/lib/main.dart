import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import 'core/constants/app_theme.dart';
import 'core/providers/auth_provider.dart';
import 'core/providers/locale_provider.dart';
import 'core/providers/theme_provider.dart';
import 'core/services/storage_service.dart';
import 'core/services/api_service.dart';
import 'core/services/auth_service.dart';
import 'core/services/kpi_service.dart';
import 'core/services/alerte_service.dart';
import 'core/services/client_service.dart';
import 'core/services/connectivity_service.dart';
import 'core/services/sync_service.dart';
import 'core/services/sse_service.dart';
import 'core/services/agent_service.dart';
import 'core/services/notification_service.dart';
import 'core/providers/sync_provider.dart';
import 'core/services/location_service.dart';
import 'router/app_router.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';

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

  final storageService      = StorageService();
  final apiService          = ApiService(storageService);
  final authService         = AuthService(apiService, storageService);
  final notificationService = NotificationService();
  final connectivityService = ConnectivityService();
  final locationService     = LocationService(apiService, connectivityService);

  await notificationService.initialize();

  runApp(
    MultiProvider(
      providers: [
        Provider<StorageService>.value(value: storageService),
        Provider<ApiService>.value(value: apiService),
        Provider<AuthService>.value(value: authService),
        Provider<NotificationService>.value(value: notificationService),
        Provider<KpiService>(create: (_) => KpiService(apiService)),
        Provider<AlerteService>(create: (_) => AlerteService(apiService)),
        Provider<ClientService>(create: (_) => ClientService(apiService)),
        Provider<AgentService>(create: (_) => AgentService(apiService)),
        Provider<ConnectivityService>.value(value: connectivityService),
        ChangeNotifierProvider<LocationService>.value(value: locationService),
        Provider<SyncService>(create: (_) => SyncService(apiService)),
        Provider<SseService>(create: (_) => SseService(storageService)),
        ChangeNotifierProvider(
          create: (ctx) => SyncProvider(
            ctx.read<SyncService>(),
            ctx.read<SseService>(),
            ctx.read<ConnectivityService>(),
          ),
        ),
        ChangeNotifierProvider(create: (_) => ThemeProvider()),
        ChangeNotifierProvider(create: (_) => LocaleProvider()),
        ChangeNotifierProvider(
          create: (_) => AuthProvider(authService, locationService: locationService),
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
    final themeProvider  = context.watch<ThemeProvider>();
    final localeProvider = context.watch<LocaleProvider>();
    final router         = AppRouter.router;

    return MaterialApp.router(
      title: 'MicroRecouv',
      debugShowCheckedModeBanner: false,
      theme:     AppTheme.lightTheme,
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
