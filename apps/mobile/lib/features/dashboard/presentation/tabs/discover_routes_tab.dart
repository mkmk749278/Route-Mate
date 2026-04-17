import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

class DiscoverRoutesTab extends StatefulWidget {
  const DiscoverRoutesTab({
    required this.controller,
    super.key,
  });

  final AppController controller;

  @override
  State<DiscoverRoutesTab> createState() => _DiscoverRoutesTabState();
}

class _DiscoverRoutesTabState extends State<DiscoverRoutesTab> {
  final _originController = TextEditingController();
  final _destinationController = TextEditingController();
  DateTime? _travelDate;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _search();
  }

  @override
  void dispose() {
    _originController.dispose();
    _destinationController.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final selected = await showDatePicker(
      context: context,
      firstDate: DateTime(now.year, now.month, now.day),
      lastDate: DateTime(now.year + 1),
      initialDate: _travelDate ?? now,
    );

    if (selected == null) {
      return;
    }

    setState(() {
      _travelDate = selected;
    });
  }

  Future<void> _search() async {
    setState(() {
      _loading = true;
    });

    try {
      await widget.controller.discoverRoutes(
        origin: _originController.text.trim(),
        destination: _destinationController.text.trim(),
        travelDate: _travelDate,
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final dateLabel = _travelDate == null
        ? 'Any date'
        : DateFormat('yyyy-MM-dd').format(_travelDate!);

    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  TextField(
                    controller: _originController,
                    decoration: const InputDecoration(labelText: 'Origin filter'),
                  ),
                  TextField(
                    controller: _destinationController,
                    decoration: const InputDecoration(
                      labelText: 'Destination filter',
                    ),
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: [
                      OutlinedButton(
                        onPressed: _pickDate,
                        child: Text(dateLabel),
                      ),
                      if (_travelDate != null)
                        OutlinedButton(
                          onPressed: () {
                            setState(() {
                              _travelDate = null;
                            });
                          },
                          child: const Text('Clear date'),
                        ),
                      FilledButton(
                        onPressed: _loading
                            ? null
                            : () {
                                _search();
                              },
                        child: _loading
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Text('Search'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: widget.controller.discoveredRoutes.isEmpty
                  ? const Center(child: Text('No routes found'))
                  : ListView.builder(
                      itemCount: widget.controller.discoveredRoutes.length,
                      itemBuilder: (context, index) {
                        final route = widget.controller.discoveredRoutes[index];
                        return Card(
                          margin: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 6,
                          ),
                          child: ListTile(
                            title: Text('${route.origin} → ${route.destination}'),
                            subtitle: Text(
                              '${DateFormat('yyyy-MM-dd').format(route.travelDate)} • '
                              '${route.preferredDepartureTime}\n'
                              'By ${route.owner.name}${route.owner.city == null ? '' : ' • ${route.owner.city}'}',
                            ),
                            isThreeLine: true,
                          ),
                        );
                      },
                    ),
            ),
          ],
        );
      },
    );
  }
}
