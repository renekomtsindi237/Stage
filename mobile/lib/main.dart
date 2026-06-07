import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'core/constants/app_theme.dart';
import 'core/providers/auth_provider.dart';
import 'core/providers/theme_provider.dart';
import 'core/services/storage_service.dart';
import 'core/services/api_service.dart';
import 'core/services/auth_service.dart';
import 'core/services/kpi_service.dart';
import 'core/services/pret_service.dart';
import 'core/services/alerte_service.dart';
import 'core/services/client_service.dart';
import 'core/services/connectivity_service.dart';
import 'router/app_router.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Orientation portrait uniquement
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  // Barre de status transparente
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
  ));

  final storageService = StorageService();
  final apiService    = ApiService(storageService);
  final authService   = AuthService(apiService, storageService);

  runApp(
    MultiProvider(
      providers: [
        Provider<StorageService>.value(value: storageService),
        Provider<ApiService>.value(value: apiService),
        Provider<AuthService>.value(value: authService),
        Provider<KpiService>(create: (_) => KpiService(apiService)),
        Provider<PretService>(create: (_) => PretService(apiService)),
        Provider<AlerteService>(create: (_) => AlerteService(apiService)),
        Provider<ClientService>(create: (_) => ClientService(apiService)),
        Provider<ConnectivityService>(create: (_) => ConnectivityService()),
        ChangeNotifierProvider(create: (_) => ThemeProvider()),
        ChangeNotifierProvider(create: (_) => AuthProvider(authService)),
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
    final router        = AppRouter.router;

    return MaterialApp.router(
      title: 'MicroRecouv',
      debugShowCheckedModeBanner: false,

      // Thème
      theme:     AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: themeProvider.themeMode,

      // Router
      routerConfig: router,
    );
  }
}
