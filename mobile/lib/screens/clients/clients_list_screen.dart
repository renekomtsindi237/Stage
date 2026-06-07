import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/models/client.dart';
import '../../core/services/client_service.dart';
import '../../widgets/app_bottom_nav.dart';
import '../../widgets/empty_state.dart';
import '../../widgets/error_widget.dart';
import '../../widgets/skeleton_loader.dart';

class ClientsListScreen extends StatefulWidget {
  const ClientsListScreen({super.key});

  @override
  State<ClientsListScreen> createState() => _ClientsListScreenState();
}

class _ClientsListScreenState extends State<ClientsListScreen> {
  final _searchController = TextEditingController();
  List<Client> _clients = [];
  bool _loading = true;
  String? _error;
  late final ClientService _clientService;
  String _lastSearch = '';

  @override
  void initState() {
    super.initState();
    _clientService = context.read<ClientService>();
    _loadClients();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadClients({String? search}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final clients = await _clientService.searchClients(search: search);
      setState(() {
        _clients = clients;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  void _onSearch(String value) {
    if (value == _lastSearch) return;
    _lastSearch = value;
    if (value.isEmpty || value.length >= 2) {
      _loadClients(search: value.isEmpty ? null : value);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.darkBg,
      appBar: AppBar(
        title: const Text('Clients'),
        backgroundColor: AppColors.darkBg,
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: Center(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.darkSurfaceRaised,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '${_clients.length}',
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: AppColors.textSecondary,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // Search bar
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: TextField(
              controller: _searchController,
              style: const TextStyle(color: Colors.white, fontFamily: 'Inter'),
              decoration: InputDecoration(
                hintText: 'Rechercher un client...',
                prefixIcon: const Icon(Icons.search_rounded, color: AppColors.textSecondary),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        onPressed: () {
                          _searchController.clear();
                          _loadClients();
                        },
                        icon: const Icon(Icons.clear, color: AppColors.textSecondary, size: 18),
                      )
                    : null,
              ),
              onChanged: _onSearch,
            ),
          ),
          // List
          Expanded(
            child: _loading
                ? ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: 8,
                    itemBuilder: (_, __) => const Padding(
                      padding: EdgeInsets.only(bottom: 8),
                      child: SkeletonListItem(),
                    ),
                  )
                : _error != null
                    ? AppErrorWidget(message: _error!, onRetry: _loadClients)
                    : _clients.isEmpty
                        ? EmptyState(
                            icon: Icons.people_outline,
                            title: 'Aucun client trouvé',
                            subtitle: 'Essayez une autre recherche',
                            onRetry: _loadClients,
                          )
                        : RefreshIndicator(
                            onRefresh: _loadClients,
                            color: AppColors.gold,
                            backgroundColor: AppColors.darkSurface,
                            child: ListView.builder(
                              padding: const EdgeInsets.symmetric(horizontal: 16),
                              itemCount: _clients.length,
                              itemBuilder: (context, index) =>
                                  _buildClientItem(_clients[index]),
                            ),
                          ),
          ),
        ],
      ),
      bottomNavigationBar: const AppBottomNav(currentIndex: 3),
    );
  }

  Widget _buildClientItem(Client client) {
    return GestureDetector(
      onTap: () => context.go('/clients/${client.idClient}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.darkSurface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.darkBorder),
        ),
        child: Row(
          children: [
            // Avatar
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [AppColors.navy, AppColors.teal],
                ),
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Text(
                  client.initials,
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: Colors.white,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    client.fullName,
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                    ),
                  ),
                  if (client.telephone != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      client.telephone!,
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                  if (client.nombrePrets != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      '${client.nombrePrets} prêt${client.nombrePrets! > 1 ? 's' : ''}',
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 11,
                        color: AppColors.teal,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: AppColors.textMuted, size: 18),
          ],
        ),
      ),
    );
  }
}
