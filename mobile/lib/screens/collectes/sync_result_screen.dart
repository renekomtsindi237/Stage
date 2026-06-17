import 'package:flutter/material.dart';
import '../../core/models/collecte_locale.dart';

class SyncResultScreen extends StatelessWidget {
  final SyncResult result;

  const SyncResultScreen({super.key, required this.result});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Résultat synchronisation')),
      body: Center(child: Text(result.resume)),
    );
  }
}
