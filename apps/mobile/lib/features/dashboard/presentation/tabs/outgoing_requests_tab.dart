import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

class OutgoingRequestsTab extends StatefulWidget {
  const OutgoingRequestsTab({
    required this.controller,
    super.key,
  });

  final AppController controller;

  @override
  State<OutgoingRequestsTab> createState() => _OutgoingRequestsTabState();
}

class _OutgoingRequestsTabState extends State<OutgoingRequestsTab> {
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
    });

    try {
      await widget.controller.fetchOutgoingInterests();
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
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        if (_loading) {
          return const Center(child: CircularProgressIndicator());
        }

        final interests = widget.controller.outgoingInterests;
        if (interests.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Text('No outgoing requests yet'),
                const SizedBox(height: 12),
                OutlinedButton(onPressed: _load, child: const Text('Refresh')),
              ],
            ),
          );
        }

        return RefreshIndicator(
          onRefresh: _load,
          child: ListView.builder(
            itemCount: interests.length,
            itemBuilder: (context, index) {
              final interest = interests[index];
              final ownerLabel = interest.owner.city == null
                  ? interest.owner.name
                  : '${interest.owner.name} • ${interest.owner.city}';
              final contactLine = interest.owner.phone == null
                  ? null
                  : 'Contact: ${interest.owner.phone}';

              return Card(
                margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${interest.route.origin} → ${interest.route.destination}',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${DateFormat('yyyy-MM-dd').format(interest.route.travelDate)} • ${interest.route.preferredDepartureTime}',
                      ),
                      const SizedBox(height: 4),
                      Text('Owner $ownerLabel'),
                      if (contactLine != null) ...[
                        const SizedBox(height: 4),
                        Text(contactLine),
                      ],
                      const SizedBox(height: 8),
                      Chip(label: Text(interest.status.toUpperCase())),
                    ],
                  ),
                ),
              );
            },
          ),
        );
      },
    );
  }
}
