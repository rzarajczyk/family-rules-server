function formatTime(hour, minute) {
    return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

class TimeOfDay {
    constructor(hour, minute) {
        this.hour = hour
        this.minute = minute
    }

    forward(minutes) {
        let newMinute = this.minutes + minutes
        let newHour = this.hours
        if (newMinute >= 60) {
            newMinute = newMinute % 60
            newHour += newMinute / 60
        }
        return new TimeOfDay(newHour, newMinute)
    }

    toMinutesOfDay() {
        return this.hour * 60 + this.minute
    }

    toPrettyString() {
        return formatTime(this.hour, this.minute)
    }

    isBefore(tod) {
        this.toMinutesOfDay() < tod.toMinutesOfDay()
    }
}

function sequence(from,to) {
    let result = []
    let current = from
    while (current.isBefore(to)) {
        result.append(current)
        current = current.forward(15)
    }
}