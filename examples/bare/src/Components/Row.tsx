import { View, StyleSheet, Text } from 'react-native';

function Row({ label, value }: { label: string; value: string | number }) {
  return (
    <View style={styles.textWrapper}>
      <Text style={styles.text}>
        {label}:{'  '}
      </Text>
      <Text style={[styles.text, styles.valueText]} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

export default Row;
const styles = StyleSheet.create({
  text: {
    fontSize: 25,
  },
  valueText: {
    width: 200,
  },
  textWrapper: {
    width: '100%',
    paddingHorizontal: 15,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
});
