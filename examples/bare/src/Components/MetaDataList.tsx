import { View, StyleSheet, Text } from 'react-native';

type MetaData = Record<string, unknown> | undefined | null;

const formatValue = (value: unknown): string => {
  if (typeof value === 'boolean') return value ? 'yes' : 'no';
  // Round the fractional values (duration, exposureTime, dpi) so they stay readable.
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : String(Math.round(value * 1000) / 1000);
  return String(value);
};

/**
 * Keys listed in `order` come first, so related fields stay grouped; anything the native side
 * returns that is not listed still shows up, appended alphabetically.
 */
const orderedEntries = (data: Record<string, unknown>, order: string[]) => {
  const rank = (key: string) => {
    const index = order.indexOf(key);
    return index === -1 ? order.length : index;
  };
  return Object.entries(data)
    .filter(([, value]) => value !== undefined && value !== null)
    .sort(([a], [b]) => rank(a) - rank(b) || a.localeCompare(b));
};

function MetaDataList({ title, data, order = [] }: { title: string; data: MetaData; order?: string[] }) {
  if (!data) return null;
  const entries = orderedEntries(data, order);
  return (
    <View style={styles.container}>
      <Text style={styles.title}>{title}</Text>
      {entries.length === 0 ? (
        <Text style={styles.empty}>no metadata</Text>
      ) : (
        entries.map(([key, value]) => (
          <View key={key} style={styles.row}>
            <Text style={styles.key} numberOfLines={1}>
              {key}
            </Text>
            <Text style={styles.value} numberOfLines={1}>
              {formatValue(value)}
            </Text>
          </View>
        ))
      )}
    </View>
  );
}

export default MetaDataList;

const styles = StyleSheet.create({
  container: {
    width: '100%',
    paddingVertical: 6,
  },
  title: {
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 2,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  key: {
    fontSize: 11,
    flexShrink: 1,
    marginRight: 8,
  },
  value: {
    fontSize: 11,
    fontWeight: '600',
  },
  empty: {
    fontSize: 11,
    fontStyle: 'italic',
  },
});
