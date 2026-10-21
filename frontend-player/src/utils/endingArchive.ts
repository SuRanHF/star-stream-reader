import { asArray, asRecord, pickText } from './panelData';

export interface EndingArchiveEntry {
  key: string;
  name: string;
  description: string;
  isHidden: boolean;
}

/** The ending endpoint returns only endings whose conditions match this character. */
export function buildEndingArchiveEntries(value: unknown): EndingArchiveEntry[] {
  return asArray(value)
    .filter((entry) => pickText(asRecord(entry), ['status'], '') !== '接口错误')
    .map((entry, index) => {
      const record = asRecord(entry);
      return {
        key: pickText(record, ['endingKey', 'ending_key', 'id'], `ending-${index}`),
        name: pickText(record, ['name', 'endingName', 'ending_name'], '已记录的个人终局'),
        description: pickText(record, ['description', 'desc'], ''),
        isHidden: record.isHidden === true || record.is_hidden === true || record.isHidden === 1 || record.is_hidden === 1,
      };
    });
}

export function hasEndingArchiveLoadError(value: unknown): boolean {
  return asArray(value).some((entry) => pickText(asRecord(entry), ['status'], '') === '接口错误');
}
