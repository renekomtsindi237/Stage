import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/models/client.dart';
import '../../core/services/client_service.dart';
import '../../widgets/error_widget.dart';
import '../../widgets/lang_switch_button.dart';
import '../../widgets/skeleton_loader.dart';

class ClientDetailScreen extends StatefulWidget {
  final String idClient;

  const ClientDetailScreen({super.key, required this.idClient});

  @override
  State<ClientDetailScreen> createState() => _ClientDetailScreenState();
}

class _ClientDetailScreenState extends State<ClientDetailScreen> {
  Client? _client;
  bool _loading = true;
  String? _error;
  late final ClientService _clientService;

  @override
  void initState() {
    super.initState();
    _clientService = context.read<ClientService>();
    _loadClient();
  }

  Future<void> _loadClient() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final client = await _clientService.getClientDetail(widget.idClient);
      setState(() {
        _client = client;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppL10n.of(context);
    return Scaffold(
      backgroundColor: context.bg,
      appBar: AppBar(
        title: Text(l10n.clientDetailTitle),
        backgroundColor: context.bg,
        leading: IconButton(
          onPressed: () => context.go('/clients'),
          icon: const Icon(Icons.arrow_back_rounded),
        ),
        actions: const [
          Padding(
            padding: EdgeInsets.only(right: 12),
            child: LangSwitchButton(onDark: false),
          ),
        ],
      ),
      body: _loading
          ? ListView(
              padding: const EdgeInsets.all(16),
              children: const [
                SkeletonDashboardHeader(),
                SizedBox(height: 16),
                SkeletonCard(),
              ],
            )
          : _error != null
              ? AppErrorWidget(message: _error!, onRetry: _loadClient)
              : _buildContent(),
    );
  }

  Widget _buildContent() {
    final client = _client!;
    final nom = client.nom;
    final prenom = client.prenom;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 24),
          decoration: BoxDecoration(
            color: context.surface,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: context.border),
            boxShadow: context.isDark
                ? []
                : [BoxShadow(color: Colors.black.withOpacity(0.06), blurRadius: 12, offset: const Offset(0, 4))],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(colors: [AppColors.navy, AppColors.teal]),
                  shape: BoxShape.circle,
                  boxShadow: [BoxShadow(color: AppColors.teal.withOpacity(0.3), blurRadius: 16, offset: const Offset(0, 4))],
                ),
                child: Center(
                  child: Text(
                    client.initials,
                    style: const TextStyle(fontFamily: 'Inter', fontSize: 30, fontWeight: FontWeight.w800, color: Colors.white),
                  ),
                ),
              ),
              const SizedBox(height: 20),
              if (prenom != null && prenom.isNotEmpty) ...[
                Text(
                  prenom,
                  style: TextStyle(fontFamily: 'Inter', fontSize: 16, fontWeight: FontWeight.w500, color: context.textSec),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 4),
              ],
              Text(
                nom,
                style: TextStyle(fontFamily: 'Inter', fontSize: 22, fontWeight: FontWeight.w800, color: context.text),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                client.idClient,
                style: TextStyle(fontFamily: 'Inter', fontSize: 12, color: context.textMut),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
