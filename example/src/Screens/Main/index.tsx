import { StackNavigationProp } from '@react-navigation/stack';
import React from 'react';
import {
  FlatList,
  StyleSheet,
  Text,
  Pressable,
  ScrollView,
  View,
} from 'react-native';

import { SCREENS, Screens } from '..';

type RootStackParams = { Home: undefined } & { [key: string]: undefined };
type MainScreenProps = {
  navigation: StackNavigationProp<RootStackParams, 'theme_screen'>;
};

const ItemSeparator = (): React.ReactElement => {
  return <View style={styles.separator} />;
};
const Index = ({ navigation }: MainScreenProps) => {
  const data = Object.keys(SCREENS).map((key) => ({ key }));
  return (
    <FlatList
      style={styles.list}
      data={data}
      ItemSeparatorComponent={ItemSeparator}
      renderItem={(props) => (
        <ScreenItem
          {...props}
          screens={SCREENS}
          onPressItem={({ key }) => navigation.navigate(key)}
        />
      )}
      renderScrollComponent={(props) => <ScrollView {...props} />}
    />
  );
};

type Item = { key: string };
type ScreenItemProps = {
  item: Item;
  onPressItem: ({ key }: Item) => void;
  screens: Screens;
};
export function ScreenItem({
  item,
  onPressItem,
  screens,
}: ScreenItemProps): React.ReactElement {
  const { key } = item;
  return (
    <Pressable style={styles.button} onPress={() => onPressItem(item)}>
      <Text style={styles.buttonText}>{screens[key].title || key}</Text>
    </Pressable>
  );
}
export default Index;
export const styles = StyleSheet.create({
  list: {
    backgroundColor: '#EFEFF4',
  },
  separator: {
    height: 1,
    backgroundColor: '#DBDBE0',
  },
  buttonText: {
    backgroundColor: 'transparent',
  },
  button: {
    flex: 1,
    height: 60,
    padding: 10,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
});
