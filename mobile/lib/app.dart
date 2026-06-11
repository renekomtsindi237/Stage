import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'core/constants/app_theme.dart';
import 'core/providers/auth_provider.dart';
import 'core/providers/theme_provider.dart';
import 'router/app_router.dart';

class MicroRecouv extends StatefulWidget {
  const MicroRecouv({super.key});

  @override
  State<MicroRecouv> createState() => _MicroRecouv();
}

class _MicroRecouv extends State<MicroRecouv> {
  @override
  Widget build(BuildContext context) {
    final themeProvider = context.watch<ThemeProvider>();
    context.watch<AuthProvider>(); // force rebuild sur changement d'auth

    final router = AppRouter.router;

    return MaterialApp.router(
      title: 'MicroRecouv',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: themeProvider.themeMode,
      routerConfig: router,
    );
  }
}
