import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:route_mates_mobile/features/shared/models/models.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

class IncomingRequestsTab extends StatefulWidget {
  const IncomingRequestsTab({
    required this.controller,
    super.key,
  });

  final AppController controller;

  @override
  State<IncomingRequestsTab> createState() => _IncomingRequestsTabState();
}

class _IncomingRequestsTabState extends State<IncomingRequestsTab> {
  bool _loading = false;
  String? _submittingInterestId;

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
      await widget.controller.fetchIncomingInterests();
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

  Future<void> _ownerDecision(RouteInterest interest, String status) async {
    try {
      setState(() {
        _submittingInterestId = interest.id;
      });
      await widget.controller.ownerDecisionRouteInterest(
        routeInterestId: interest.id,
        status: status,
      );
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Request ${status == 'accepted' ? 'accepted' : 'rejected'}')),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    } finally {
      if (mounted) {
        setState(() {
          _submittingInterestId = null;
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

        final interests = widget.controller.incomingInterests;
        if (interests.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Text('No incoming requests yet'),
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
              final requesterLabel = interest.requester.city == null
                  ? interest.requester.name
                  : '${interest.requester.name} • ${interest.requester.city}';
              final contactLine = interest.requester.phone == null
                  ? null
                  : 'Contact: ${interest.requester.phone}';

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
                      Text('From $requesterLabel'),
                      if (contactLine != null) ...[
                        const SizedBox(height: 4),
                        Text(contactLine),
                      ],
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          Chip(label: Text(interest.status.toUpperCase())),
                          if (interest.status == 'pending')
                            FilledButton(
                              onPressed: _submittingInterestId == interest.id
                                  ? null
                                  : () => _ownerDecision(interest, 'accepted'),
                              child: _submittingInterestId == interest.id
                                  ? const SizedBox(
                                      width: 16,
                                      height: 16,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                      ),
                                    )
                                  : const Text('Accept'),
                            ),
                          if (interest.status == 'pending')
                            OutlinedButton(
                              onPressed: _submittingInterestId == interest.id
                                  ? null
                                  : () => _ownerDecision(interest, 'rejected'),
                              child: const Text('Reject'),
                            ),
                        ],
                      ),
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
