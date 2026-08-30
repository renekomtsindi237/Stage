import 'package:flutter/foundation.dart';

/// Signale à GoRouter qu'il doit recalculer les redirections (session 24 h, logout).
final authRefresh = ValueNotifier<int>(0);

void notifyAuthChanged() => authRefresh.value++;
