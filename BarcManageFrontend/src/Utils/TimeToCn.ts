export const timestampToCn = (time: Date | string): string => {
  const date = new Date(time);
  const options: Intl.DateTimeFormatOptions = {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false // 24小时制
  };
  return date.toLocaleString('zh-CN', options);
};
