package com.smartspend.data.db

import androidx.room.TypeConverter
import com.smartspend.data.models.*

class Converters {
    @TypeConverter fun fromPaymentMethod(v: PaymentMethod) = v.name
    @TypeConverter fun toPaymentMethod(v: String) = PaymentMethod.valueOf(v)

    @TypeConverter fun fromBillType(v: BillType) = v.name
    @TypeConverter fun toBillType(v: String) = BillType.valueOf(v)

    @TypeConverter fun fromRecurrence(v: RecurrenceFrequency) = v.name
    @TypeConverter fun toRecurrence(v: String) = RecurrenceFrequency.valueOf(v)

    @TypeConverter fun fromRecurrenceNullable(v: RecurrenceFrequency?) = v?.name
    @TypeConverter fun toRecurrenceNullable(v: String?) = v?.let { RecurrenceFrequency.valueOf(it) }

    @TypeConverter fun fromKhataEntryType(v: KhataEntryType) = v.name
    @TypeConverter fun toKhataEntryType(v: String) = KhataEntryType.valueOf(v)

    @TypeConverter fun fromKhataEntryStatus(v: KhataEntryStatus) = v.name
    @TypeConverter fun toKhataEntryStatus(v: String) = KhataEntryStatus.valueOf(v)

    @TypeConverter fun fromTransactionCategory(v: TransactionCategory) = v.name
    @TypeConverter fun toTransactionCategory(v: String) = TransactionCategory.valueOf(v)

    @TypeConverter fun fromTransactionSource(v: TransactionSource) = v.name
    @TypeConverter fun toTransactionSource(v: String) = TransactionSource.valueOf(v)

    @TypeConverter fun fromEntryType(v: EntryType) = v.name
    @TypeConverter fun toEntryType(v: String) = EntryType.valueOf(v)
}
