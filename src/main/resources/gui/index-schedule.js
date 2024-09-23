function formatTime(t) {
    return `${String(t.hour).padStart(2, '0')}:${String(t.minute).padStart(2, '0')}`
}

function timeId(t) {
    return formatTime(t).replace(':', '')
}

function createTimetableData() {
    return {
        hours: Array.from({ length: 96 }, (_, i) => {
                   const hour = Math.floor(i / 4);  // There are 4 slots per hour (00, 15, 30, 45)
                   const minute = (i % 4) * 15;     // Minutes are in increments of 15
                   const timeFormatted =  formatTime({hour, minute});
                   const t = timeId({hour, minute})
                   return { hour, minute, timeFormatted, timeId: t };
                 }),
        days: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
    }
}

function sequence(from, to) {
    const result = [];

    let currentHour = from.hour;
    let currentMinute = from.minute;

    while (currentHour < to.hour || (currentHour === to.hour && currentMinute < to.minute)) {
        result.push({ hour: currentHour, minute: currentMinute });

        // Add 15 minutes
        currentMinute += 15;
        if (currentMinute >= 60) {
            currentMinute -= 60;
            currentHour += 1;
        }
    }

    return result;
}